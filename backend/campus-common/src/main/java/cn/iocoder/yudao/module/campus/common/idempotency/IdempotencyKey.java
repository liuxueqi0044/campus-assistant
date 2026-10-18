package cn.iocoder.yudao.module.campus.common.idempotency;

import java.util.Objects;

/** The database identity of one command. The raw client key is deliberately not retained. */
public record IdempotencyKey(long platformTenantId, long actorId, String action, String keyHash) {

    public static final int ACTION_MAX_LENGTH = 128;
    public static final int HASH_LENGTH = 64;

    public IdempotencyKey {
        if (platformTenantId < 0 || actorId < 0) {
            throw new IllegalArgumentException("tenant and actor ids must not be negative");
        }
        action = requireText(action, "action");
        keyHash = requireText(keyHash, "keyHash");
        if (action.length() > ACTION_MAX_LENGTH) {
            throw new IllegalArgumentException("action must not exceed " + ACTION_MAX_LENGTH + " characters");
        }
        if (!keyHash.matches("[0-9a-fA-F]{" + HASH_LENGTH + "}")) {
            throw new IllegalArgumentException("keyHash must be a SHA-256 hexadecimal digest");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
