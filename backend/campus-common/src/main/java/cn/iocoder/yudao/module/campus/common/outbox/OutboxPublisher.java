package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;
import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelopeValidator;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.util.Objects;

/** Publishes a fact into the caller's already active, read-write business transaction. */
public final class OutboxPublisher {
    private final OutboxRepository repository;
    private final int maxAttempts;
    private final Clock clock;
    public OutboxPublisher(OutboxRepository repository, int maxAttempts) { this(repository, maxAttempts, Clock.systemUTC()); }
    public OutboxPublisher(OutboxRepository repository, int maxAttempts, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.maxAttempts = maxAttempts; this.clock = Objects.requireNonNull(clock, "clock");
    }
    public void publish(CampusEventEnvelope event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("outbox publish requires an existing read-write transaction");
        }
        CampusEventEnvelopeValidator.validate(event);
        repository.insertIfAbsent(event, maxAttempts, clock.instant());
        OutboxRecord persisted = repository.find(event.getEventId())
                .orElseThrow(() -> new IllegalStateException("outbox event disappeared after insert"));
        if (!OutboxEnvelopeEquality.same(event, persisted.envelope())) {
            throw new OutboxEventConflictException();
        }
    }
}
