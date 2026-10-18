package cn.iocoder.yudao.module.campus.common.idempotency;

import java.time.Duration;
import java.util.Objects;

/** Input supplied by an application service to the durable command executor. */
public record IdempotentCommand(
        long platformTenantId,
        long actorId,
        String action,
        String key,
        Object request,
        Duration retention) {

    public static final int KEY_MAX_LENGTH = 256;

    public IdempotentCommand {
        if (platformTenantId < 0 || actorId < 0) {
            throw new IllegalArgumentException("tenant and actor ids must not be negative");
        }
        action = required(action, "action");
        key = required(key, "key");
        if (action.length() > IdempotencyKey.ACTION_MAX_LENGTH) {
            throw new IllegalArgumentException("action must not exceed " + IdempotencyKey.ACTION_MAX_LENGTH
                    + " characters");
        }
        if (key.length() > KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("key must not exceed " + KEY_MAX_LENGTH + " characters");
        }
        retention = retention == null ? Duration.ofDays(7) : retention;
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("retention must be positive");
        }
        if (retention.compareTo(Duration.ofDays(90)) > 0) {
            throw new IllegalArgumentException("retention must not exceed 90 days");
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
