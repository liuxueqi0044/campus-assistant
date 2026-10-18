package cn.iocoder.yudao.module.campus.identity.api;

import java.util.Arrays;

/** Stable M01.04 actions; each also requires its upstream RBAC permission. */
public enum OperatorAction {
    VERIFICATION_REVIEW("campus:verification:review"),
    MEMBERSHIP_REVOKE("campus:membership:revoke"),
    OPERATOR_GRANT_MANAGE("campus:operator-grant:manage");

    private final String permission;

    OperatorAction(String permission) {
        this.permission = permission;
    }

    public String permission() {
        return permission;
    }

    public static OperatorAction reviewAction(String value) {
        if (VERIFICATION_REVIEW.name().equals(value)) {
            return VERIFICATION_REVIEW;
        }
        if (MEMBERSHIP_REVOKE.name().equals(value)) {
            return MEMBERSHIP_REVOKE;
        }
        throw new IllegalArgumentException("unsupported review authorization action");
    }

    public static OperatorAction storedAction(String value) {
        return Arrays.stream(values()).filter(action -> action.name().equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown stored operator action"));
    }
}
