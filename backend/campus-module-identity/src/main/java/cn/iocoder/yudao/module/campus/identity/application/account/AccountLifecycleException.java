package cn.iocoder.yudao.module.campus.identity.application.account;

import java.util.Objects;

/** Internal M01.06 failure; L owns its eventual public HTTP/error-code mapping. */
public final class AccountLifecycleException extends RuntimeException {

    private final AccountLifecycleFailure failure;

    public AccountLifecycleException(AccountLifecycleFailure failure) {
        super(Objects.requireNonNull(failure, "failure").name());
        this.failure = failure;
    }

    public AccountLifecycleFailure failure() {
        return failure;
    }
}
