package cn.iocoder.yudao.module.campus.common.transaction;

/**
 * A whitelisted database lock conflict consumed every bounded attempt.
 * The API adapter maps this stable semantic to {@code RETRYABLE_CONFLICT}; it must not pretend
 * that the command completed.
 */
public final class TransactionRetryExhaustedException extends RuntimeException {

    public static final String CODE = "RETRYABLE_CONFLICT";

    private final String operation;
    private final int attempts;

    public TransactionRetryExhaustedException(String operation, int attempts, Throwable cause) {
        super(CODE + ": operation=" + operation + ", attempts=" + attempts, cause);
        this.operation = operation;
        this.attempts = attempts;
    }

    public String getOperation() {
        return operation;
    }

    public int getAttempts() {
        return attempts;
    }
}
