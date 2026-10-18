package cn.iocoder.yudao.module.campus.identity.application;

/** Internal M01.03 failure names; the integration owner maps them to the frozen public error registry. */
public enum MembershipVerificationFailure {
    MEDIA_NOT_OWNED,
    MEDIA_NOT_READY,
    VERIFICATION_CONFLICT,
    MEMBERSHIP_REQUIRED,
    MEMBERSHIP_EXPIRED,
    MEMBERSHIP_REVOKED,
    VERIFICATION_METHOD_DISABLED,
    VERIFICATION_REVIEW_FORBIDDEN,
    ACCOUNT_DISABLED,
    RESOURCE_NOT_FOUND
}
