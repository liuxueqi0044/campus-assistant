package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void permanentFailureDoesNotPreventAnotherConsumer() {
        MemoryRepository repository = new MemoryRepository(row("e-1", 1, 3));
        List<String> handled = new ArrayList<>();
        EventHandlerRegistry registry = new EventHandlerRegistry(List.of(
                consumer("reject", ConsumerExecutionResult.PERMANENT_FAILURE, handled),
                consumer("other", ConsumerExecutionResult.COMPLETED, handled)));

        newDispatcher(repository, registry).dispatchBatch();

        assertThat(handled).containsExactly("reject", "other");
        assertThat(repository.deadLetterCodes).containsExactly("CONSUMER_PERMANENT_FAILURE");
    }

    @Test
    void retrySchedulesBeforeMaxAndDeadLettersAtMax() {
        MemoryRepository retry = new MemoryRepository(row("e-1", 1, 3));
        newDispatcher(retry, new EventHandlerRegistry(List.of(
                consumer("one", ConsumerExecutionResult.RETRYABLE_FAILURE, new ArrayList<>())))).dispatchBatch();
        assertThat(retry.retryCalls).isEqualTo(1);
        assertThat(retry.deadLetterCodes).isEmpty();

        MemoryRepository exhausted = new MemoryRepository(row("e-2", 3, 3));
        newDispatcher(exhausted, new EventHandlerRegistry(List.of(
                consumer("one", ConsumerExecutionResult.RETRYABLE_FAILURE, new ArrayList<>())))).dispatchBatch();
        assertThat(exhausted.deadLetterCodes).containsExactly("CAMPUS_OUTBOX_RETRY_EXHAUSTED");
    }

    @Test
    void backoffIsStableAndBounded() {
        OutboxDispatcher dispatcher = newDispatcher(new MemoryRepository(), new EventHandlerRegistry(List.of()));
        Instant first = dispatcher.backoffAt("e-1", 1, NOW);
        Instant again = dispatcher.backoffAt("e-1", 1, NOW);
        Instant capped = dispatcher.backoffAt("e-1", 30, NOW);
        assertThat(first).isEqualTo(again).isAfter(NOW);
        assertThat(capped.isBefore(NOW.plusSeconds(10)) || capped.equals(NOW.plusSeconds(10))).isTrue();
    }

    @Test
    void leaseLostRowDoesNotAbortTheRestOfBatch() {
        MemoryRepository repository = new MemoryRepository(row("e-1", 1, 3), row("e-2", 1, 3));
        repository.leaseLostIds.add("e-1");
        List<String> handled = new ArrayList<>();
        newDispatcher(repository, new EventHandlerRegistry(List.of(
                consumer("one", ConsumerExecutionResult.COMPLETED, handled)))).dispatchBatch();
        assertThat(handled).containsExactly("one");
        assertThat(repository.succeededIds).containsExactly("e-2");
    }

    @Test
    void failedHandlerRollsBackThenRecordsFailureInNewTransaction() {
        List<String> timeline = new ArrayList<>();
        MemoryRepository repository = new MemoryRepository(timeline, row("e-1", 1, 3));
        RecordingTransactionManager manager = new RecordingTransactionManager(timeline);
        EventHandlerRegistry registry = new EventHandlerRegistry(List.of(new OutboxConsumer() {
            public String name() { return "one"; }
            public String eventType() { return "campus.test.created"; }
            public int eventVersion() { return 1; }
            public ConsumerExecutionResult handle(CampusEventEnvelope event) { throw new IllegalStateException("secret"); }
        }));
        newDispatcher(manager, repository, registry).dispatchBatch();
        assertThat(repository.operations).contains("failConsumer");
        assertThat(timeline).containsSubsequence("rollback", "failConsumer", "commit");
    }

    @Test
    void succeededConsumptionSkipsHandler() {
        MemoryRepository repository = new MemoryRepository(row("e-1", 1, 3));
        repository.consumerStatuses.put("one", ConsumerStatus.SUCCEEDED);
        List<String> handled = new ArrayList<>();
        newDispatcher(repository, new EventHandlerRegistry(List.of(
                consumer("one", ConsumerExecutionResult.COMPLETED, handled)))).dispatchBatch();
        assertThat(handled).isEmpty();
    }

    private static OutboxDispatcher newDispatcher(MemoryRepository repository, EventHandlerRegistry registry) {
        return newDispatcher(new RecordingTransactionManager(), repository, registry);
    }

    private static OutboxDispatcher newDispatcher(RecordingTransactionManager manager,
                                                  MemoryRepository repository, EventHandlerRegistry registry) {
        return new OutboxDispatcher(manager, repository, registry, "worker", 10,
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofSeconds(10), CLOCK);
    }

    private static OutboxConsumer consumer(String name, ConsumerExecutionResult result, List<String> handled) {
        return new OutboxConsumer() {
            public String name() { return name; }
            public String eventType() { return "campus.test.created"; }
            public int eventVersion() { return 1; }
            public ConsumerExecutionResult handle(CampusEventEnvelope event) { handled.add(name); return result; }
        };
    }

    private static OutboxRecord row(String id, int attempt, int maxAttempts) {
        CampusEventEnvelope event = CampusEventEnvelope.builder().eventId(id).type("campus.test.created")
                .version(1).aggregateId("1").aggregateVersion(1).schoolId("1")
                .occurredAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)).payload(Map.of("safe", "yes")).build();
        return new OutboxRecord(event, OutboxStatus.PROCESSING, NOW, attempt, maxAttempts,
                "worker", NOW.plusSeconds(30), 1, null, null, null, null, 1,
                null, null, null, null, NOW, NOW);
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {
        final List<String> operations = new ArrayList<>();
        private final List<String> timeline;
        private final TransactionStatus status = new TransactionStatus() {
            private boolean rollbackOnly;
            public boolean isNewTransaction() { return true; }
            public void setRollbackOnly() { rollbackOnly = true; }
            public boolean isRollbackOnly() { return rollbackOnly; }
            public void flush() { }
            public boolean isCompleted() { return false; }
            public boolean hasSavepoint() { return false; }
            public Object createSavepoint() { return new Object(); }
            public void rollbackToSavepoint(Object savepoint) { }
            public void releaseSavepoint(Object savepoint) { }
        };
        RecordingTransactionManager() { this(new ArrayList<>()); }
        RecordingTransactionManager(List<String> timeline) { this.timeline = timeline; }
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            operations.add("begin:" + definition.getPropagationBehavior());
            timeline.add("begin:" + definition.getPropagationBehavior());
            return status;
        }
        public void commit(TransactionStatus transactionStatus) { operations.add("commit"); timeline.add("commit"); }
        public void rollback(TransactionStatus transactionStatus) { operations.add("rollback"); timeline.add("rollback"); }
    }

    private static final class MemoryRepository implements OutboxRepository {
        final List<OutboxRecord> rows = new ArrayList<>();
        final Map<String, ConsumerStatus> consumerStatuses = new HashMap<>();
        final List<String> operations = new ArrayList<>();
        final List<String> succeededIds = new ArrayList<>();
        final List<String> deadLetterCodes = new ArrayList<>();
        final java.util.Set<String> leaseLostIds = new java.util.HashSet<>();
        private final List<String> timeline;
        int retryCalls;
        MemoryRepository(OutboxRecord... rows) { this(new ArrayList<>(), rows); }
        MemoryRepository(List<String> timeline, OutboxRecord... rows) { this.timeline = timeline; this.rows.addAll(List.of(rows)); }
        public void insertIfAbsent(CampusEventEnvelope e, int max, Instant now) { }
        public Optional<OutboxRecord> find(String id) { return rows.stream().filter(r -> r.envelope().getEventId().equals(id)).findFirst(); }
        public Optional<ConsumerStatus> consumptionStatus(String eventId, String consumer) { return Optional.ofNullable(consumerStatuses.get(consumer)); }
        public List<OutboxRecord> claim(String owner, int limit, Instant now, Instant leaseUntil) { return rows; }
        public void assertLease(String id, long token, Instant now) { if (leaseLostIds.contains(id)) throw new OutboxLeaseLostException(id); }
        public ConsumerStatus startConsumer(String id, String consumer, long token, Instant now) { assertLease(id, token, now); return consumerStatuses.getOrDefault(consumer, ConsumerStatus.PROCESSING); }
        public void succeedConsumer(String id, String consumer, long token, Instant now) { operations.add("succeedConsumer"); consumerStatuses.put(consumer, ConsumerStatus.SUCCEEDED); }
        public void failConsumer(String id, String consumer, long token, String code, String reason, Instant now) { operations.add("failConsumer"); timeline.add("failConsumer"); consumerStatuses.put(consumer, ConsumerStatus.FAILED); }
        public void renew(String id, long token, Instant until, Instant now) { }
        public void retry(String id, long token, Instant next, String consumer, String code, String reason, Instant now) { retryCalls++; }
        public void deadLetter(String id, long token, String consumer, String code, String reason, Instant now) { deadLetterCodes.add(code); }
        public void succeed(String id, long token, String consumer, Instant now) { succeededIds.add(id); }
        public boolean replay(String id, String consumer, String reason, long expected, Instant now) { return true; }
    }
}
