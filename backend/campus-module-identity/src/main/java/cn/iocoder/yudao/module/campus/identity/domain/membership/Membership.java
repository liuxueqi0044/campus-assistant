package cn.iocoder.yudao.module.campus.identity.domain.membership;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Authoritative membership scope. Expiry is derived from the injected server Clock. */
public record Membership(long id, long userId, long schoolId, long campusId,
                         MembershipStatus status, Instant effectiveFrom, Instant expiresAt,
                         long scopeVersion) {

    public Membership {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (id <= 0 || userId <= 0 || schoolId <= 0 || campusId <= 0 || scopeVersion <= 0) {
            throw new IllegalArgumentException("membership identifiers and version must be positive");
        }
        if (!expiresAt.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("membership expiry must be after effective time");
        }
    }

    public MembershipEvaluation evaluate(long requestedCampusId, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (status == MembershipStatus.REVOKED) {
            return MembershipEvaluation.REVOKED;
        }
        Instant now = clock.instant();
        if (!now.isBefore(expiresAt)) {
            return MembershipEvaluation.EXPIRED;
        }
        if (requestedCampusId != campusId || now.isBefore(effectiveFrom)) {
            return MembershipEvaluation.REQUIRED;
        }
        return MembershipEvaluation.EFFECTIVE;
    }
}
