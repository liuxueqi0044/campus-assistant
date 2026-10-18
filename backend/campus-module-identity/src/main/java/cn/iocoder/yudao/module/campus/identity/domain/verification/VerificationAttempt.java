package cn.iocoder.yudao.module.campus.identity.domain.verification;

import java.util.Objects;

/** Pure state machine for one immutable-source verification attempt. */
public record VerificationAttempt(long id, long userId, long schoolId, long campusId,
                                  VerificationStatus status, long version) {

    public VerificationAttempt {
        Objects.requireNonNull(status, "status");
        if (id <= 0 || userId <= 0 || schoolId <= 0 || campusId <= 0 || version <= 0) {
            throw new IllegalArgumentException("verification identifiers and version must be positive");
        }
    }

    public VerificationAttempt transition(VerificationAction action, long expectedVersion) {
        Objects.requireNonNull(action, "action");
        if (version != expectedVersion) {
            throw new IllegalStateException("verification version conflict");
        }
        VerificationStatus target = switch (action) {
            case APPROVE -> requirePending(VerificationStatus.APPROVED);
            case REJECT -> requirePending(VerificationStatus.REJECTED);
            case REVOKE -> requireApproved();
        };
        return new VerificationAttempt(id, userId, schoolId, campusId, target, version + 1);
    }

    private VerificationStatus requirePending(VerificationStatus target) {
        if (status != VerificationStatus.PENDING) {
            throw new IllegalStateException("only a pending verification can be reviewed");
        }
        return target;
    }

    private VerificationStatus requireApproved() {
        if (status != VerificationStatus.APPROVED) {
            throw new IllegalStateException("only an approved verification can be revoked");
        }
        return VerificationStatus.REVOKED;
    }
}
