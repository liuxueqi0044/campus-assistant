package cn.iocoder.yudao.module.campus.identity.domain.runner;

import java.util.Objects;

public final class RunnerQualificationAssertionException extends IllegalStateException {
    public enum Reason {
        EXPIRED, NOT_YET_EFFECTIVE, NOT_APPROVED, SUSPENDED, REVOKED,
        USER_SCOPE_DENIED, SCHOOL_SCOPE_DENIED, CAMPUS_SCOPE_DENIED
    }

    private final Reason reason;

    public RunnerQualificationAssertionException(Reason reason) {
        super(Objects.requireNonNull(reason, "reason").name());
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
