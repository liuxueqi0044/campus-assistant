package cn.iocoder.yudao.module.campus.common.idempotency;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentCommandExecutorTest {

    private final FakeRepository repository = new FakeRepository();
    private final IdempotentCommandExecutor executor = new IdempotentCommandExecutor(
            callbackTransactions(repository), repository, new RequestCanonicalizer(new ObjectMapper()), new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void secondSameRequestReplaysWithoutRunningHandlerAndRechecksAuthorization() {
        AtomicInteger handlerCalls = new AtomicInteger();
        AtomicInteger authorizationCalls = new AtomicInteger();
        IdempotentCommand command = command(Map.of("amount", 10));

        IdempotentCommandResult<String> first = executor.execute(command, String.class,
                () -> new CommandExecution<>("accepted", "ORDER-1", 1),
                ignored -> authorizationCalls.incrementAndGet());
        IdempotentCommandResult<String> second = executor.execute(command, String.class,
                () -> {
                    handlerCalls.incrementAndGet();
                    return new CommandExecution<>("wrong", "ORDER-2", 1);
                }, ignored -> authorizationCalls.incrementAndGet());

        assertThat(first.replayed()).isFalse();
        assertThat(second).isEqualTo(new IdempotentCommandResult<>("accepted", true, "ORDER-1", 1));
        assertThat(handlerCalls).hasValue(0);
        assertThat(authorizationCalls).hasValue(1);
    }

    @Test
    void differentRequestWithSameKeyIsAConflict() {
        IdempotentCommand command = command(Map.of("amount", 10));
        executor.execute(command, String.class, () -> new CommandExecution<>("accepted", "ORDER-1", 1),
                ReplayAuthorizer.allowAll());

        assertThatThrownBy(() -> executor.execute(command(Map.of("amount", 11)), String.class,
                () -> new CommandExecution<>("wrong", "ORDER-2", 1), ReplayAuthorizer.allowAll()))
                .isInstanceOf(CampusApiException.class)
                .extracting(error -> ((CampusApiException) error).getSpec().getName())
                .isEqualTo("CAMPUS_IDEMPOTENCY_CONFLICT");
        assertThat(repository.rows).hasSize(1);
    }

    @Test
    void authorizationRejectionDoesNotRemoveCompletedRecord() {
        IdempotentCommand command = command(Map.of("amount", 12));
        executor.execute(command, String.class, () -> new CommandExecution<>("accepted", "ORDER-3", 1),
                ReplayAuthorizer.allowAll());

        assertThatThrownBy(() -> executor.execute(command, String.class,
                () -> { throw new AssertionError("handler must not run on replay"); },
                ignored -> { throw new CampusApiException(cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes.PERMISSION_DENIED); }))
                .isInstanceOf(CampusApiException.class);
        assertThat(repository.rows).hasSize(1);
    }

    @Test
    void committedProcessingRecordReturnsInProgressWithoutRunningHandler() {
        IdempotentCommand command = command(Map.of("amount", 13));
        RequestCanonicalizer.CanonicalRequest canonical = new RequestCanonicalizer(new ObjectMapper())
                .canonicalize(command.request());
        IdempotencyKey key = new IdempotencyKey(command.platformTenantId(), command.actorId(), command.action(),
                sha256(command.key()));
        repository.putProcessing(key, canonical.hash(), Instant.parse("2026-01-08T00:00:00Z"));
        AtomicInteger handlerCalls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(command, String.class, () -> {
            handlerCalls.incrementAndGet();
            return new CommandExecution<>("wrong", "ORDER-4", 1);
        }, ReplayAuthorizer.allowAll()))
                .isInstanceOf(CampusApiException.class)
                .extracting(error -> ((CampusApiException) error).getSpec().getName())
                .isEqualTo("CAMPUS_COMMAND_IN_PROGRESS");
        assertThat(handlerCalls).hasValue(0);
    }

    @Test
    void failedHandlerDoesNotLeaveReservation() {
        IdempotentCommand command = command(Map.of("name", "retryable"));
        assertThatThrownBy(() -> executor.execute(command, String.class,
                () -> { throw new IllegalStateException("business failure"); }, ReplayAuthorizer.allowAll()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.rows).isEmpty();
    }

    private static IdempotentCommand command(Object body) {
        return new IdempotentCommand(1, 7, "order.create", "client-key-1", body, null);
    }

    private static TransactionOperations callbackTransactions(FakeRepository repository) {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                FakeRepository.Snapshot snapshot = repository.snapshot();
                try {
                    return action.doInTransaction(null);
                } catch (RuntimeException exception) {
                    repository.restore(snapshot);
                    throw exception;
                }
            }
        };
    }

    private static final class FakeRepository implements IdempotencyRepository {
        private final Map<IdempotencyKey, IdempotencyRecord> rows = new HashMap<>();
        private long sequence;

        private void putProcessing(IdempotencyKey key, String requestHash, Instant expiresAt) {
            rows.put(key, new IdempotencyRecord(++sequence, key, requestHash,
                    IdempotencyStatus.PROCESSING, "committed-processing-token", null, null, 1, expiresAt));
        }

        @Override
        public IdempotencyReservation reserve(IdempotencyKey key, String requestHash, Instant expiresAt,
                                              String processingToken) {
            IdempotencyRecord existing = rows.get(key);
            if (existing != null) {
                return new IdempotencyReservation(existing, false);
            }
            IdempotencyRecord created = new IdempotencyRecord(++sequence, key, requestHash,
                    IdempotencyStatus.PROCESSING, processingToken, null, null, 1, expiresAt);
            rows.put(key, created);
            return new IdempotencyReservation(created, true);
        }

        @Override
        public void complete(IdempotencyKey key, String processingToken, String businessReference,
                             long resultVersion, String resultJson) {
            IdempotencyRecord existing = rows.get(key);
            rows.put(key, new IdempotencyRecord(existing.id(), key, existing.requestHash(),
                    IdempotencyStatus.SUCCEEDED, null, businessReference, resultJson, resultVersion,
                    existing.expiresAt()));
        }

        private Snapshot snapshot() {
            return new Snapshot(new HashMap<>(rows), sequence);
        }

        private void restore(Snapshot snapshot) {
            rows.clear();
            rows.putAll(snapshot.rows());
            sequence = snapshot.sequence();
        }

        private record Snapshot(Map<IdempotencyKey, IdempotencyRecord> rows, long sequence) {
        }
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
