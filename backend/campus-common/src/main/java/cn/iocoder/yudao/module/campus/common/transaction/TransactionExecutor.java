package cn.iocoder.yudao.module.campus.common.transaction;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/**
 * Executes an application callback in one {@code PROPAGATION_REQUIRED} transaction, with the
 * small retry whitelist described by {@link TransactionPolicy}.
 *
 * <p>Do not invoke gateways, HTTP clients, mail/SMS, or payment SDKs from the callback. Such
 * effects belong behind M00.05 outbox delivery. Retrying here deliberately starts the <em>whole</em>
 * transaction again; preserving an idempotency key is the caller's responsibility.</p>
 */
public final class TransactionExecutor {

    private static final Duration BASE_BACKOFF = Duration.ofMillis(25);

    private final TransactionOperations transactions;
    private final RetryPause retryPause;

    public TransactionExecutor(PlatformTransactionManager transactionManager) {
        this(requiredTemplate(transactionManager), TransactionExecutor::pauseWithBoundedBackoff);
    }

    TransactionExecutor(TransactionOperations transactions, RetryPause retryPause) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.retryPause = Objects.requireNonNull(retryPause, "retryPause must not be null");
    }

    /** Runs the callback in one REQUIRED transaction, retrying only the explicit policy whitelist. */
    public <T> T required(TransactionPolicy policy, TransactionCallback<T> callback) {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                return transactions.execute(callback);
            } catch (RuntimeException exception) {
                if (!isRetryable(exception, policy) || attempt == policy.maxAttempts()) {
                    if (isRetryable(exception, policy)) {
                        throw new TransactionRetryExhaustedException(policy.operation(), attempt, exception);
                    }
                    throw exception;
                }
                retryPause.pause(attempt);
            }
        }
        throw new IllegalStateException("unreachable retry loop state");
    }

    private static TransactionTemplate requiredTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return template;
    }

    private static boolean isRetryable(Throwable failure, TransactionPolicy policy) {
        if (policy.retryableErrors().isEmpty()) {
            return false;
        }
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (policy.retryableErrors().contains(RetryableTransactionError.MYSQL_DEADLOCK)
                    && (current instanceof DeadlockLoserDataAccessException
                    || mysqlErrorCode(current, 1213))) {
                return true;
            }
            if (policy.retryableErrors().contains(RetryableTransactionError.MYSQL_LOCK_WAIT_TIMEOUT)
                    && (current instanceof CannotAcquireLockException
                    || mysqlErrorCode(current, 1205))) {
                return true;
            }
            // Some Connector/J versions expose a generic SQLTransactionRollbackException without
            // preserving a Spring DAO subtype. It is still only eligible when a whitelisted code
            // above was present, never merely because it is a concurrency exception.
            if (current instanceof SQLTransactionRollbackException rollback) {
                if (rollback.getErrorCode() == 1213
                        && policy.retryableErrors().contains(RetryableTransactionError.MYSQL_DEADLOCK)) {
                    return true;
                }
                if (rollback.getErrorCode() == 1205
                        && policy.retryableErrors().contains(RetryableTransactionError.MYSQL_LOCK_WAIT_TIMEOUT)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean mysqlErrorCode(Throwable current, int expectedCode) {
        return current instanceof SQLException sqlException && sqlException.getErrorCode() == expectedCode;
    }

    private static void pauseWithBoundedBackoff(int retryNumber) {
        long multiplier = 1L << Math.min(retryNumber - 1, 2);
        LockSupport.parkNanos(BASE_BACKOFF.toNanos() * multiplier);
    }

    @FunctionalInterface
    interface RetryPause {
        void pause(int retryNumber);
    }
}
