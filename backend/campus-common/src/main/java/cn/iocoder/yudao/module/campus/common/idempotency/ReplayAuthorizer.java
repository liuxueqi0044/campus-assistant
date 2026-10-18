package cn.iocoder.yudao.module.campus.common.idempotency;

/** Re-checks the caller's current permission before a persisted result is replayed. */
@FunctionalInterface
public interface ReplayAuthorizer {

    void authorize(IdempotencyRecord record);

    static ReplayAuthorizer allowAll() {
        return record -> { };
    }
}
