package cn.iocoder.yudao.module.campus.common.transaction;

import java.util.Objects;
import java.util.Set;

/**
 * Explicit retry contract for one application operation.
 *
 * <p>Most commands must use {@link #withoutRetry(String)}. The retry form is intentionally small:
 * at most three total attempts and only MySQL lock conflicts are eligible. Optimistic version
 * conflicts are not retried; callers must expose them as {@code VERSION_CONFLICT}.</p>
 */
public record TransactionPolicy(String operation, Set<RetryableTransactionError> retryableErrors, int maxAttempts) {

    public static final int MAX_ALLOWED_ATTEMPTS = 3;

    public TransactionPolicy {
        Objects.requireNonNull(operation, "operation must not be null");
        if (operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        retryableErrors = retryableErrors == null ? Set.of() : Set.copyOf(retryableErrors);
        if (maxAttempts < 1 || maxAttempts > MAX_ALLOWED_ATTEMPTS) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and " + MAX_ALLOWED_ATTEMPTS);
        }
        if (retryableErrors.isEmpty() && maxAttempts != 1) {
            throw new IllegalArgumentException("a no-retry policy must have exactly one attempt");
        }
    }

    public static TransactionPolicy withoutRetry(String operation) {
        return new TransactionPolicy(operation, Set.of(), 1);
    }

    public static TransactionPolicy retryMySqlLockConflicts(String operation, int maxAttempts) {
        return new TransactionPolicy(operation, Set.of(
                RetryableTransactionError.MYSQL_DEADLOCK,
                RetryableTransactionError.MYSQL_LOCK_WAIT_TIMEOUT), maxAttempts);
    }
}
