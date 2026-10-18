package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorSpec;

import java.util.Objects;

public final class MembershipVerificationException extends CampusApiException {

    private final MembershipVerificationFailure failure;

    public MembershipVerificationException(MembershipVerificationFailure failure) {
        super(toPublicSpec(Objects.requireNonNull(failure, "failure")));
        this.failure = failure;
    }

    public MembershipVerificationFailure failure() {
        return failure;
    }

    private static CampusErrorSpec toPublicSpec(MembershipVerificationFailure failure) {
        return switch (failure) {
            case MEDIA_NOT_OWNED -> CampusErrorCodes.MEDIA_NOT_OWNED;
            case MEDIA_NOT_READY -> CampusErrorCodes.MEDIA_NOT_READY;
            case VERIFICATION_CONFLICT -> CampusErrorCodes.VERIFICATION_CONFLICT;
            case MEMBERSHIP_REQUIRED -> CampusErrorCodes.MEMBERSHIP_REQUIRED;
            case MEMBERSHIP_EXPIRED -> CampusErrorCodes.MEMBERSHIP_EXPIRED;
            case MEMBERSHIP_REVOKED -> CampusErrorCodes.MEMBERSHIP_REVOKED;
            case VERIFICATION_METHOD_DISABLED -> CampusErrorCodes.VERIFICATION_METHOD_DISABLED;
            case VERIFICATION_REVIEW_FORBIDDEN -> CampusErrorCodes.VERIFICATION_REVIEW_FORBIDDEN;
            case ACCOUNT_DISABLED -> CampusErrorCodes.ACCOUNT_DISABLED;
            case RESOURCE_NOT_FOUND -> CampusErrorCodes.RESOURCE_NOT_FOUND;
        };
    }
}
