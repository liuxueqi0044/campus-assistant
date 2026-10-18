package cn.iocoder.yudao.module.campus.common.idempotency;

/** Durable lifecycle of an idempotency command. */
public enum IdempotencyStatus {
    PROCESSING,
    SUCCEEDED
}
