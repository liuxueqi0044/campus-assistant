package cn.iocoder.yudao.module.campus.common.idempotency;

/** Result returned to the application service, including whether this was a replay. */
public record IdempotentCommandResult<T>(
        T result,
        boolean replayed,
        String businessReference,
        long resultVersion) {
}
