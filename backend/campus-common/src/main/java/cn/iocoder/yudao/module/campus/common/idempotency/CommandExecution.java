package cn.iocoder.yudao.module.campus.common.idempotency;

import java.util.Objects;

/** Business output that is safe to persist for a later semantic replay. */
public record CommandExecution<T>(T result, String businessReference, long resultVersion) {

    public CommandExecution {
        businessReference = Objects.requireNonNull(businessReference, "businessReference must not be null");
        if (businessReference.isBlank()) {
            throw new IllegalArgumentException("businessReference must not be blank");
        }
        if (resultVersion < 1) {
            throw new IllegalArgumentException("resultVersion must be positive");
        }
    }
}
