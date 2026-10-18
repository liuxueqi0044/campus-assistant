package cn.iocoder.yudao.module.campus.identity.domain.account;

import cn.iocoder.yudao.module.campus.identity.api.AccountCapability;
import cn.iocoder.yudao.module.campus.identity.api.AccountLifecycleStatus;

import java.time.Instant;
import java.util.Objects;

/** M01.06 state layered over the upstream member account; CLOSED is terminal. */
public record AccountLifecycle(long userId, AccountLifecycleStatus status,
                               long authVersion, long version,
                               Instant closureRequestedAt, Instant closedAt) {

    public AccountLifecycle {
        Objects.requireNonNull(status, "status");
        if (userId <= 0 || authVersion < 0 || version < 0) {
            throw new IllegalArgumentException("account lifecycle identifiers and versions are invalid");
        }
        if (authVersion != version) {
            throw new IllegalArgumentException("lifecycle auth and row versions must match");
        }
        validateClosureShape(status, closureRequestedAt, closedAt);
    }

    public static AccountLifecycle implicitActive(long userId) {
        return new AccountLifecycle(userId, AccountLifecycleStatus.ACTIVE, 0, 0, null, null);
    }

    public boolean allows(AccountCapability capability) {
        Objects.requireNonNull(capability, "capability");
        return switch (capability) {
            case NEW_SENSITIVE_ACTION -> status == AccountLifecycleStatus.ACTIVE;
            case OWN_REMEDIATION -> status == AccountLifecycleStatus.ACTIVE
                    || status == AccountLifecycleStatus.LIMITED
                    || status == AccountLifecycleStatus.CLOSURE_PENDING;
            case PRIVATE_RESOURCE_READ -> status == AccountLifecycleStatus.ACTIVE
                    || status == AccountLifecycleStatus.LIMITED
                    || status == AccountLifecycleStatus.CLOSURE_PENDING;
        };
    }

    public AccountLifecycle transition(AccountLifecycleStatus target, Instant now) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(now, "now");
        if (!canTransition(status, target)) {
            throw new IllegalStateException("account lifecycle transition is not allowed");
        }
        Instant requestedAt = target == AccountLifecycleStatus.CLOSURE_PENDING
                ? now : target == AccountLifecycleStatus.CLOSED ? closureRequestedAt : null;
        Instant completedAt = target == AccountLifecycleStatus.CLOSED ? now : null;
        return new AccountLifecycle(userId, target, authVersion + 1, version + 1,
                requestedAt, completedAt);
    }

    private static boolean canTransition(AccountLifecycleStatus source, AccountLifecycleStatus target) {
        if (source == target || source == AccountLifecycleStatus.CLOSED) {
            return false;
        }
        return switch (source) {
            case ACTIVE, LIMITED, SUSPENDED -> target == AccountLifecycleStatus.ACTIVE
                    || target == AccountLifecycleStatus.LIMITED
                    || target == AccountLifecycleStatus.SUSPENDED
                    || target == AccountLifecycleStatus.CLOSURE_PENDING;
            case CLOSURE_PENDING -> target == AccountLifecycleStatus.ACTIVE
                    || target == AccountLifecycleStatus.SUSPENDED
                    || target == AccountLifecycleStatus.CLOSED;
            case CLOSED -> false;
        };
    }

    private static void validateClosureShape(AccountLifecycleStatus status,
                                             Instant closureRequestedAt, Instant closedAt) {
        boolean valid = switch (status) {
            case ACTIVE, LIMITED, SUSPENDED -> closureRequestedAt == null && closedAt == null;
            case CLOSURE_PENDING -> closureRequestedAt != null && closedAt == null;
            case CLOSED -> closureRequestedAt != null && closedAt != null
                    && !closedAt.isBefore(closureRequestedAt);
        };
        if (!valid) {
            throw new IllegalArgumentException("account lifecycle closure timestamps do not match status");
        }
    }
}
