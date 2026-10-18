package cn.iocoder.yudao.module.campus.common.transaction;

/**
 * The only database failures that a caller may put on the automatic retry whitelist.
 *
 * <p>A retry always re-runs the complete {@code REQUIRED} transaction. It is therefore suitable
 * only for an operation whose database effects are idempotent (normally protected by a business
 * unique key / idempotency key) and that has not performed an external side effect.</p>
 */
public enum RetryableTransactionError {
    MYSQL_DEADLOCK,
    MYSQL_LOCK_WAIT_TIMEOUT
}
