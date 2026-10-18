package cn.iocoder.yudao.module.campus.identity.application.authorization;

/** Internal M01.04 failures; L owns their eventual public HTTP mapping. */
public enum OperatorAuthorizationFailure {
    OPERATOR_DISABLED,
    SCOPE_DENIED,
    GRANT_EXPIRED,
    ACTION_DENIED,
    GRANT_NOT_FOUND,
    GRANT_CONFLICT
}
