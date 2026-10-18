package cn.iocoder.yudao.module.campus.common.idempotency;

/** Result of an atomic reservation attempt. */
public record IdempotencyReservation(IdempotencyRecord record, boolean claimed) {
}
