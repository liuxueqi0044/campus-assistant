package cn.iocoder.yudao.module.campus.common.idempotency;

import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorDetail;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Executes a durable command and its business write in one REQUIRED transaction.
 *
 * <p>The repository uses a no-op upsert instead of throwing a duplicate-key exception. This is
 * important: a duplicate-key exception marks a MySQL transaction rollback-only, so querying the
 * winner in that same transaction is not safe. The upsert waits for the winner and the subsequent
 * {@code SELECT ... FOR UPDATE} observes its committed result without poisoning the transaction.</p>
 */
public class IdempotentCommandExecutor {

    private final TransactionOperations transactions;
    private final IdempotencyRepository repository;
    private final RequestCanonicalizer canonicalizer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotentCommandExecutor(PlatformTransactionManager transactionManager,
                                     IdempotencyRepository repository,
                                     RequestCanonicalizer canonicalizer,
                                     ObjectMapper objectMapper) {
        this(requiredTemplate(transactionManager), repository, canonicalizer, objectMapper, Clock.systemUTC());
    }

    public IdempotentCommandExecutor(TransactionOperations transactions,
                                     IdempotencyRepository repository,
                                     RequestCanonicalizer canonicalizer,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public <T> IdempotentCommandResult<T> execute(IdempotentCommand command, Class<T> resultType,
                                                   IdempotentCommandHandler<T> handler,
                                                   ReplayAuthorizer replayAuthorizer) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(resultType, "resultType must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        Objects.requireNonNull(replayAuthorizer, "replayAuthorizer must not be null");

        RequestCanonicalizer.CanonicalRequest canonical = canonicalizer.canonicalize(command.request());
        IdempotencyKey key = new IdempotencyKey(command.platformTenantId(), command.actorId(), command.action(),
                RequestCanonicalizerHash.sha256(command.key()));
        Instant expiresAt = clock.instant().plus(command.retention());
        String processingToken = UUID.randomUUID().toString();

        return transactions.execute(status -> {
            IdempotencyReservation reservation = repository.reserve(key, canonical.hash(), expiresAt,
                    processingToken);
            IdempotencyRecord record = reservation.record();
            if (!reservation.claimed()) {
                if (!canonical.hash().equals(record.requestHash())) {
                    throw new CampusApiException(CampusErrorCodes.IDEMPOTENCY_CONFLICT);
                }
                if (record.status() == IdempotencyStatus.PROCESSING) {
                    throw new CampusApiException(CampusErrorCodes.COMMAND_IN_PROGRESS,
                            CampusErrorDetail.of(CampusErrorCodes.COMMAND_IN_PROGRESS.getRetryAction()));
                }
                replayAuthorizer.authorize(record);
                return replay(record, resultType);
            }

            CommandExecution<T> execution = Objects.requireNonNull(handler.execute(),
                    "command handler result must not be null");
            String resultJson;
            try {
                resultJson = objectMapper.writeValueAsString(execution.result());
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("command result cannot be persisted", exception);
            }
            repository.complete(key, processingToken, execution.businessReference(), execution.resultVersion(),
                    resultJson);
            return new IdempotentCommandResult<>(execution.result(), false, execution.businessReference(),
                    execution.resultVersion());
        });
    }

    private <T> IdempotentCommandResult<T> replay(IdempotencyRecord record, Class<T> resultType) {
        try {
            T result = record.resultJson() == null ? null : objectMapper.readValue(record.resultJson(), resultType);
            return new IdempotentCommandResult<>(result, true, record.businessReference(), record.resultVersion());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("persisted idempotency result cannot be decoded", exception);
        }
    }

    private static TransactionOperations requiredTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return template;
    }

    /** Kept private to avoid exposing raw client keys in any persistent or diagnostic model. */
    private static final class RequestCanonicalizerHash {
        private RequestCanonicalizerHash() {
        }

        private static String sha256(String value) {
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder result = new StringBuilder(bytes.length * 2);
                for (byte item : bytes) {
                    result.append(String.format("%02x", item));
                }
                return result.toString();
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is required by the JDK", exception);
            }
        }
    }
}
