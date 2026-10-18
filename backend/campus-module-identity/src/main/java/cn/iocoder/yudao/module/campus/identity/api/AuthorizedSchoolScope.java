package cn.iocoder.yudao.module.campus.identity.api;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Authorization proof returned by M01.04; callers must not synthesize it from request data. */
public record AuthorizedSchoolScope(long operatorId, OperatorAction action,
                                    long schoolId, Long campusId,
                                    long grantId, String grantScopeType,
                                    long grantVersion, long authorizationVersion,
                                    OffsetDateTime expiresAt) {

    public AuthorizedSchoolScope {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(grantScopeType, "grantScopeType");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (operatorId <= 0 || schoolId <= 0 || campusId != null && campusId <= 0
                || grantId <= 0 || grantVersion <= 0 || authorizationVersion <= 0) {
            throw new IllegalArgumentException("authorized scope identifiers and versions must be positive");
        }
    }
}
