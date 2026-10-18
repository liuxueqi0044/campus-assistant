package cn.iocoder.yudao.module.campus.common.idempotency;

import java.time.Instant;

/** A sanitized database row; it never contains the original request or client key. */
public record IdempotencyRecord(
        long id,
        IdempotencyKey key,
        String requestHash,
        IdempotencyStatus status,
        String processingToken,
        String businessReference,
        String resultJson,
        long resultVersion,
        Instant expiresAt) {

    public IdempotencyRecord {
        if (requestHash == null || !requestHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("requestHash must be a SHA-256 hexadecimal digest");
        }
        if (businessReference != null && businessReference.length() > 128) {
            throw new IllegalArgumentException("businessReference must not exceed 128 characters");
        }
    }
}
