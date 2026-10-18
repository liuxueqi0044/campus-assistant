package cn.iocoder.yudao.module.campus.identity.domain.authorization;

import cn.iocoder.yudao.module.campus.identity.api.OperatorAction;
import cn.iocoder.yudao.module.campus.identity.api.RequestedSchoolScope;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** An explicit scope grant intersected with upstream RBAC at authorization time. */
public record OperatorGrant(long id, long operatorId, OperatorScopeType scopeType,
                            Long schoolId, Long campusId, Set<OperatorAction> actions,
                            OperatorGrantStatus status, Instant validFrom, Instant expiresAt,
                            long version) {

    public OperatorGrant {
        Objects.requireNonNull(scopeType, "scopeType");
        actions = Set.copyOf(Objects.requireNonNull(actions, "actions"));
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (id <= 0 || operatorId <= 0 || version <= 0 || actions.isEmpty()) {
            throw new IllegalArgumentException("grant identifiers, version and actions must be present");
        }
        validateScope(scopeType, schoolId, campusId);
        if (!expiresAt.isAfter(validFrom)) {
            throw new IllegalArgumentException("grant expiry must be after validFrom");
        }
    }

    public boolean isEffectiveFor(OperatorAction action, RequestedSchoolScope requested, Clock clock) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(clock, "clock");
        Instant now = clock.instant();
        return status == OperatorGrantStatus.ACTIVE && !now.isBefore(validFrom) && now.isBefore(expiresAt)
                && actions.contains(action) && covers(requested);
    }

    public boolean covers(RequestedSchoolScope requested) {
        Objects.requireNonNull(requested, "requested");
        return switch (scopeType) {
            case PLATFORM -> true;
            case SCHOOL -> schoolId == requested.schoolId();
            case CAMPUS -> requested.campusId() != null
                    && schoolId == requested.schoolId() && campusId.equals(requested.campusId());
        };
    }

    public boolean isCurrent(Clock clock) {
        Instant now = Objects.requireNonNull(clock, "clock").instant();
        return status == OperatorGrantStatus.ACTIVE && !now.isBefore(validFrom) && now.isBefore(expiresAt);
    }

    public int specificity() {
        return switch (scopeType) {
            case PLATFORM -> 0;
            case SCHOOL -> 1;
            case CAMPUS -> 2;
        };
    }

    public static void validateScope(OperatorScopeType scopeType, Long schoolId, Long campusId) {
        boolean valid = switch (Objects.requireNonNull(scopeType, "scopeType")) {
            case PLATFORM -> schoolId == null && campusId == null;
            case SCHOOL -> schoolId != null && schoolId > 0 && campusId == null;
            case CAMPUS -> schoolId != null && schoolId > 0 && campusId != null && campusId > 0;
        };
        if (!valid) {
            throw new IllegalArgumentException("grant scope shape does not match scopeType");
        }
    }
}
