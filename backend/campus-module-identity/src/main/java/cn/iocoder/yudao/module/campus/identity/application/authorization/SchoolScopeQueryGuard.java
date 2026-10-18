package cn.iocoder.yudao.module.campus.identity.application.authorization;

import cn.iocoder.yudao.module.campus.identity.api.AuthorizedSchoolQuery;
import cn.iocoder.yudao.module.campus.identity.api.AuthorizedSchoolScope;
import cn.iocoder.yudao.module.campus.identity.api.OperatorAction;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

/** Converts an authorization proof to an exact typed query condition and fails closed without one. */
@Component
public class SchoolScopeQueryGuard {

    private final Clock clock;

    public SchoolScopeQueryGuard() {
        this(Clock.systemUTC());
    }

    SchoolScopeQueryGuard(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AuthorizedSchoolQuery require(AuthorizedSchoolScope scope, OperatorAction expectedAction) {
        if (scope == null || scope.schoolId() <= 0 || scope.grantId() <= 0
                || scope.grantVersion() <= 0 || scope.authorizationVersion() <= 0
                || !scope.expiresAt().isAfter(OffsetDateTime.now(clock))) {
            throw new OperatorAuthorizationException(OperatorAuthorizationFailure.SCOPE_DENIED);
        }
        if (expectedAction == null || scope.action() != expectedAction) {
            throw new OperatorAuthorizationException(OperatorAuthorizationFailure.ACTION_DENIED);
        }
        try {
            switch (scope.grantScopeType()) {
                case "PLATFORM" -> {
                    // Platform and school proofs may carry the requested campus as a server-owned condition.
                }
                case "SCHOOL" -> {
                }
                case "CAMPUS" -> {
                    if (scope.campusId() == null || scope.campusId() <= 0) {
                        throw new OperatorAuthorizationException(OperatorAuthorizationFailure.SCOPE_DENIED);
                    }
                }
                default -> throw new OperatorAuthorizationException(OperatorAuthorizationFailure.SCOPE_DENIED);
            }
        } catch (NullPointerException exception) {
            throw new OperatorAuthorizationException(OperatorAuthorizationFailure.SCOPE_DENIED);
        }
        return new AuthorizedSchoolQuery(scope.schoolId(), scope.campusId(),
                scope.grantId(), scope.authorizationVersion());
    }
}
