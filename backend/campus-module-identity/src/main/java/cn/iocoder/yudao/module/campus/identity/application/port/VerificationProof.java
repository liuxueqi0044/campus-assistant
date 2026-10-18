package cn.iocoder.yudao.module.campus.identity.application.port;

import java.time.Instant;
import java.util.Objects;

/** Opaque, normalized proof returned by a media/email/school adapter. */
public record VerificationProof(String reference, Instant validFrom, Instant expiresAt) {

    public VerificationProof {
        reference = Objects.requireNonNull(reference, "reference").trim();
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (reference.isEmpty() || reference.length() > 128 || reference.contains("://")) {
            throw new IllegalArgumentException("proof reference must be an opaque identifier");
        }
        if (!expiresAt.isAfter(validFrom)) {
            throw new IllegalArgumentException("proof expiry must be after validFrom");
        }
    }
}
