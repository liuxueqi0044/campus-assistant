package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Claims in short transactions, then runs each consumer after the claim commits. */
public final class OutboxDispatcher {
    private static final String UNKNOWN_VERSION = CampusErrorCodes.EVENT_VERSION_UNSUPPORTED.getName();
    private static final String UNKNOWN_CONSUMER = CampusErrorCodes.EVENT_VERSION_UNSUPPORTED.getName();
    private static final String RETRYABLE = "CONSUMER_RETRYABLE_FAILURE";
    private static final String PERMANENT = "CONSUMER_PERMANENT_FAILURE";
    private static final String RETRY_EXHAUSTED = CampusErrorCodes.OUTBOX_RETRY_EXHAUSTED.getName();

    private final OutboxRepository repository;
    private final EventHandlerRegistry registry;
    private final TransactionTemplate claimTransactions;
    private final TransactionTemplate requiredTransactions;
    private final Clock clock;
    private final String owner;
    private final int batchSize;
    private final Duration lease;
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    public OutboxDispatcher(PlatformTransactionManager manager, OutboxRepository repository,
                            EventHandlerRegistry registry, String owner, int batchSize,
                            Duration lease, Duration initialBackoff, Duration maxBackoff) {
        this(manager, repository, registry, owner, batchSize, lease, initialBackoff, maxBackoff,
                Clock.systemUTC());
    }

    public OutboxDispatcher(PlatformTransactionManager manager, OutboxRepository repository,
                            EventHandlerRegistry registry, String owner, int batchSize,
                            Duration lease, Duration initialBackoff, Duration maxBackoff, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (batchSize < 1 || lease.isNegative() || lease.isZero() || initialBackoff.isNegative()
                || initialBackoff.isZero() || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("invalid outbox timings");
        }
        this.batchSize = batchSize;
        this.lease = lease;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.claimTransactions = new TransactionTemplate(Objects.requireNonNull(manager));
        this.claimTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiredTransactions = new TransactionTemplate(manager);
        this.requiredTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    /** Returns number claimed; a stale row cannot abort processing of other claimed rows. */
    public int dispatchBatch() {
        Instant now = clock.instant();
        List<OutboxRecord> claimed = claimTransactions.execute(transaction ->
                repository.claim(owner, batchSize, now, now.plus(lease)));
        if (claimed == null) return 0;
        for (OutboxRecord row : claimed) {
            try {
                dispatch(row);
            } catch (OutboxLeaseLostException staleWorker) {
                // A newer worker owns this row. Every write with this token is fenced; continue the batch.
            }
        }
        return claimed.size();
    }

    private void dispatch(OutboxRecord row) {
        List<OutboxConsumer> consumers = selectConsumers(row);
        String eventId = row.envelope().getEventId();
        if (consumers.isEmpty()) {
            String code = row.targetConsumer() == null ? UNKNOWN_VERSION : UNKNOWN_CONSUMER;
            String reason = row.targetConsumer() == null
                    ? "no handler registered for event type/version"
                    : "replay target consumer is not registered for this event version";
            repository.deadLetter(eventId, row.leaseToken(), row.targetConsumer(), code, reason, clock.instant());
            return;
        }

        ConsumerExecutionResult aggregate = ConsumerExecutionResult.COMPLETED;
        String lastConsumer = null;
        for (OutboxConsumer consumer : consumers) {
            ConsumerExecutionResult result = runConsumer(row, consumer);
            lastConsumer = consumer.name();
            if (result == ConsumerExecutionResult.PERMANENT_FAILURE) {
                aggregate = ConsumerExecutionResult.PERMANENT_FAILURE;
            } else if (result == ConsumerExecutionResult.RETRYABLE_FAILURE
                    && aggregate == ConsumerExecutionResult.COMPLETED) {
                aggregate = ConsumerExecutionResult.RETRYABLE_FAILURE;
            }
        }

        Instant now = clock.instant();
        if (aggregate == ConsumerExecutionResult.COMPLETED) {
            repository.succeed(eventId, row.leaseToken(), lastConsumer, now);
        } else if (aggregate == ConsumerExecutionResult.PERMANENT_FAILURE) {
            repository.deadLetter(eventId, row.leaseToken(), lastConsumer, PERMANENT,
                    "one or more consumers permanently rejected the event", now);
        } else if (row.attempt() >= row.maxAttempts()) {
            repository.deadLetter(eventId, row.leaseToken(), lastConsumer, RETRY_EXHAUSTED,
                    "maximum delivery attempts exhausted", now);
        } else {
            repository.retry(eventId, row.leaseToken(), backoffAt(eventId, row.attempt(), now),
                    lastConsumer, RETRYABLE, "one or more consumers requested retry", now);
        }
    }

    private List<OutboxConsumer> selectConsumers(OutboxRecord row) {
        if (row.targetConsumer() == null) return registry.handlersFor(row.envelope());
        return registry.handlerFor(row.envelope(), row.targetConsumer()).map(List::of).orElse(List.of());
    }

    private ConsumerExecutionResult runConsumer(OutboxRecord row, OutboxConsumer consumer) {
        String eventId = row.envelope().getEventId();
        try {
            return requiredTransactions.execute(transaction -> {
                ConsumerStatus old = repository.startConsumer(eventId, consumer.name(), row.leaseToken(), clock.instant());
                if (old == ConsumerStatus.SUCCEEDED) return ConsumerExecutionResult.COMPLETED;
                ConsumerExecutionResult outcome = Objects.requireNonNull(consumer.handle(row.envelope()), "consumer result");
                if (outcome != ConsumerExecutionResult.COMPLETED) {
                    transaction.setRollbackOnly();
                    throw new ConsumerOutcome(outcome);
                }
                repository.succeedConsumer(eventId, consumer.name(), row.leaseToken(), clock.instant());
                return outcome;
            });
        } catch (ConsumerOutcome outcome) {
            persistConsumerFailure(row, consumer, outcome.result == ConsumerExecutionResult.PERMANENT_FAILURE
                    ? PERMANENT : RETRYABLE, "consumer returned " + outcome.result.name());
            return outcome.result;
        } catch (OutboxLeaseLostException lost) {
            throw lost;
        } catch (RuntimeException failure) {
            persistConsumerFailure(row, consumer, "HANDLER_EXCEPTION", "handler failed: "
                    + failure.getClass().getSimpleName());
            return ConsumerExecutionResult.RETRYABLE_FAILURE;
        }
    }

    /** Failure recording has its own REQUIRED transaction after handler effects have rolled back. */
    private void persistConsumerFailure(OutboxRecord row, OutboxConsumer consumer,
                                        String errorCode, String reason) {
        requiredTransactions.execute(transaction -> {
            repository.failConsumer(row.envelope().getEventId(), consumer.name(), row.leaseToken(),
                    errorCode, reason, clock.instant());
            return null;
        });
    }

    /** Exponential delay with stable bounded jitter; no exception/payload text is persisted. */
    Instant backoffAt(String eventId, int attempt, Instant now) {
        long multiplier = 1L << Math.min(30, Math.max(0, attempt - 1));
        long base = Math.min(maxBackoff.toMillis(), safeMultiply(initialBackoff.toMillis(), multiplier));
        long jitter = Math.floorMod(Arrays.hashCode((eventId + ":" + attempt)
                .getBytes(StandardCharsets.UTF_8)), Math.max(1, base / 5 + 1));
        return now.plusMillis(Math.min(maxBackoff.toMillis(), base + jitter));
    }

    private static long safeMultiply(long first, long second) {
        try {
            return Math.multiplyExact(first, second);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static final class ConsumerOutcome extends RuntimeException {
        private final ConsumerExecutionResult result;
        private ConsumerOutcome(ConsumerExecutionResult result) { this.result = result; }
    }
}
