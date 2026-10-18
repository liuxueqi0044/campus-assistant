package cn.iocoder.yudao.module.campus.identity.application.authorization;

import java.util.Objects;

public final class OperatorAuthorizationException extends RuntimeException {

    private final OperatorAuthorizationFailure failure;

    public OperatorAuthorizationException(OperatorAuthorizationFailure failure) {
        super(Objects.requireNonNull(failure, "failure").name());
        this.failure = failure;
    }

    public OperatorAuthorizationFailure failure() {
        return failure;
    }
}
