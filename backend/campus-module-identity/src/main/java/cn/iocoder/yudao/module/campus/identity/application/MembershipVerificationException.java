package cn.iocoder.yudao.module.campus.identity.application;

import java.util.Objects;

public final class MembershipVerificationException extends RuntimeException {

    private final MembershipVerificationFailure failure;

    public MembershipVerificationException(MembershipVerificationFailure failure) {
        super(Objects.requireNonNull(failure, "failure").name());
        this.failure = failure;
    }

    public MembershipVerificationFailure failure() {
        return failure;
    }
}
