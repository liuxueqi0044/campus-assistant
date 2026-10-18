package cn.iocoder.yudao.module.campus.identity.application.account;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.api.AccountCapability;
import cn.iocoder.yudao.module.campus.identity.api.AccountLifecycleApi;
import cn.iocoder.yudao.module.campus.identity.api.AccountLifecycleSnapshot;
import cn.iocoder.yudao.module.campus.identity.api.AccountLifecycleStatus;
import cn.iocoder.yudao.module.campus.identity.application.port.AccountObligation;
import cn.iocoder.yudao.module.campus.identity.application.port.AccountObligationPort;
import cn.iocoder.yudao.module.campus.identity.application.port.AccountStatusPort;
import cn.iocoder.yudao.module.campus.identity.application.port.OperatorAccountPort;
import cn.iocoder.yudao.module.campus.identity.application.port.SessionRevocationPort;
import cn.iocoder.yudao.module.campus.identity.application.port.UpstreamMemberAccountPort;
import cn.iocoder.yudao.module.campus.identity.domain.account.AccountLifecycle;
import cn.iocoder.yudao.module.campus.identity.infrastructure.AccountLifecycleRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.AccountLifecycleDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.AccountLifecycleLogDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountLifecycleService implements AccountLifecycleApi, AccountStatusPort {

    static final String MANAGE_PERMISSION = "campus:account:lifecycle:manage";

    private final AccountLifecycleRepository repository;
    private final UpstreamMemberAccountPort upstreamMemberAccountPort;
    private final OperatorAccountPort operatorAccountPort;
    private final AccountObligationPort obligationPort;
    private final SessionRevocationPort sessionRevocationPort;
    private final SessionRevocationCompletionService sessionRevocationCompletionService;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Override
    public AccountLifecycleSnapshot snapshot(long userId) {
        requireUser(userId);
        upstreamMemberAccountPort.assertActive(userId);
        return snapshot(toDomain(userId, repository.find(userId)));
    }

    @Override
    public void assertAllowed(long userId, AccountCapability capability) {
        requireUser(userId);
        Objects.requireNonNull(capability, "capability");
        upstreamMemberAccountPort.assertActive(userId);
        AccountLifecycle lifecycle = toDomain(userId, repository.find(userId));
        if (!lifecycle.allows(capability)) {
            throw failure(AccountLifecycleFailure.ACCOUNT_ACTION_RESTRICTED);
        }
    }

    /** Existing M01.03 callers mean "may start a new sensitive action" when they ask for ACTIVE. */
    @Override
    public void assertActive(long userId) {
        try {
            assertAllowed(userId, AccountCapability.NEW_SENSITIVE_ACTION);
        } catch (AccountLifecycleException restricted) {
            throw new CampusApiException(CampusErrorCodes.ACCOUNT_DISABLED);
        }
    }

    public AccountLifecycleResult changeRestriction(long actorId, ChangeAccountLifecycleCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.targetStatus() != AccountLifecycleStatus.ACTIVE
                && command.targetStatus() != AccountLifecycleStatus.LIMITED
                && command.targetStatus() != AccountLifecycleStatus.SUSPENDED) {
            throw new IllegalArgumentException("administrative restriction target is unsupported");
        }
        assertLifecycleManager(actorId);
        upstreamMemberAccountPort.assertActive(command.userId());
        String reason = requireReason(command.reason());
        return transition(command.userId(), actorId, "OPERATOR", command.targetStatus(), reason,
                command.expectedVersion());
    }

    public AccountClosureResult requestClosure(long userId, RequestAccountClosureCommand command) {
        Objects.requireNonNull(command, "command");
        requireUser(userId);
        upstreamMemberAccountPort.assertActive(userId);
        String reason = requireReason(command.reason());
        AccountLifecycle before = toDomain(userId, repository.find(userId));
        if (before.version() != command.expectedVersion()) {
            throw failure(AccountLifecycleFailure.LIFECYCLE_CONFLICT);
        }
        List<AccountObligation> blockers = List.copyOf(
                Objects.requireNonNull(obligationPort.findBlockers(userId), "obligationPort result"));
        if (!blockers.isEmpty()) {
            return new AccountClosureResult(userId, before.status(), before.authVersion(), before.version(),
                    blockers, blockers.get(0).nextAction());
        }
        AccountLifecycleResult changed = transition(userId, userId, "MEMBER",
                AccountLifecycleStatus.CLOSURE_PENDING, reason, command.expectedVersion());
        return new AccountClosureResult(userId, changed.status(), changed.authVersion(), changed.version(),
                List.of(), "WAIT_FOR_CLOSURE_COMPLETION");
    }

    public void retrySessionRevocation(long userId) {
        requireUser(userId);
        AccountLifecycleDO row = repository.find(userId);
        if (row != null && "PENDING".equals(row.getSessionRevocationState())) {
            revokeSessions(row.getUserId(), row.getAuthVersion());
        }
    }

    private AccountLifecycleResult transition(long userId, long actorId, String actorType,
                                              AccountLifecycleStatus target, String reason,
                                              long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }
        Transitioned transitioned = Objects.requireNonNull(transactions.execute(status -> {
            AccountLifecycleDO row = repository.lock(userId);
            AccountLifecycle source = toDomain(userId, row);
            if (source.version() != expectedVersion) {
                throw failure(AccountLifecycleFailure.LIFECYCLE_CONFLICT);
            }
            AccountLifecycle changed;
            try {
                changed = source.transition(target, clock.instant());
            } catch (IllegalStateException invalid) {
                throw failure(AccountLifecycleFailure.LIFECYCLE_CONFLICT);
            }
            if (row == null) {
                insert(changed, reason, actorType, actorId);
            } else if (!repository.transition(userId, source.status().name(), changed.status().name(),
                    changed.authVersion(), source.version(), changed.version(), reason,
                    local(changed.closureRequestedAt()), local(changed.closedAt()), actorType, actorId)) {
                throw failure(AccountLifecycleFailure.LIFECYCLE_CONFLICT);
            }
            appendLog(source, changed, reason, actorType, actorId, clock.instant());
            scheduleSessionRevocation(changed.userId(), changed.authVersion());
            return new Transitioned(changed, "PENDING");
        }));
        AccountLifecycle changed = transitioned.lifecycle();
        return new AccountLifecycleResult(changed.userId(), changed.status(), changed.authVersion(),
                changed.version(), transitioned.sessionRevocationState());
    }

    private void insert(AccountLifecycle changed, String reason, String actorType, long actorId) {
        AccountLifecycleDO row = new AccountLifecycleDO();
        row.setUserId(changed.userId());
        row.setStatus(changed.status().name());
        row.setAuthVersion(changed.authVersion());
        row.setVersion(changed.version());
        row.setStatusReason(reason);
        row.setClosureRequestedAt(local(changed.closureRequestedAt()));
        row.setClosedAt(local(changed.closedAt()));
        row.setSessionRevocationState("PENDING");
        row.setLastTransitionActorType(actorType);
        row.setLastTransitionBy(actorId);
        row.setCreator(String.valueOf(actorId));
        row.setUpdater(String.valueOf(actorId));
        repository.insert(row);
    }

    private void appendLog(AccountLifecycle source, AccountLifecycle changed, String reason,
                           String actorType, long actorId, Instant occurredAt) {
        AccountLifecycleLogDO logRow = new AccountLifecycleLogDO();
        logRow.setUserId(changed.userId());
        logRow.setFromStatus(source.version() == 0 ? "IMPLICIT_ACTIVE" : source.status().name());
        logRow.setToStatus(changed.status().name());
        logRow.setReason(reason);
        logRow.setActorType(actorType);
        logRow.setActorId(actorId);
        logRow.setLifecycleVersion(changed.version());
        logRow.setAuthVersion(changed.authVersion());
        logRow.setOccurredAt(local(occurredAt));
        repository.appendLog(logRow);
    }

    private void scheduleSessionRevocation(long userId, long authVersion) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            revokeSessions(userId, authVersion);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                revokeSessions(userId, authVersion);
            }
        });
    }

    private void revokeSessions(long userId, long authVersion) {
        try {
            sessionRevocationPort.revokeAll(userId);
            sessionRevocationCompletionService.markComplete(userId, authVersion);
        } catch (RuntimeException unavailable) {
            log.warn("Account session revocation remains pending for user {} authVersion {}",
                    userId, authVersion);
        }
    }

    private void assertLifecycleManager(long actorId) {
        if (actorId <= 0) {
            throw failure(AccountLifecycleFailure.ACCOUNT_ACTION_RESTRICTED);
        }
        operatorAccountPort.assertActive(actorId);
        if (!operatorAccountPort.hasPermission(actorId, MANAGE_PERMISSION)) {
            throw failure(AccountLifecycleFailure.ACCOUNT_ACTION_RESTRICTED);
        }
    }

    private static AccountLifecycle toDomain(long userId, AccountLifecycleDO row) {
        if (row == null) {
            return AccountLifecycle.implicitActive(userId);
        }
        try {
            return new AccountLifecycle(row.getUserId(), AccountLifecycleStatus.valueOf(row.getStatus()),
                    row.getAuthVersion(), row.getVersion(), instant(row.getClosureRequestedAt()),
                    instant(row.getClosedAt()));
        } catch (RuntimeException malformed) {
            // Persisted lifecycle state is security-sensitive. Any shape we do not
            // understand must deny the action before a write, log, or revocation.
            throw failure(AccountLifecycleFailure.ACCOUNT_ACTION_RESTRICTED);
        }
    }

    private static AccountLifecycleSnapshot snapshot(AccountLifecycle lifecycle) {
        return new AccountLifecycleSnapshot(lifecycle.userId(), lifecycle.status(),
                lifecycle.authVersion(), lifecycle.version());
    }

    private static String requireReason(String value) {
        String reason = Objects.requireNonNull(value, "reason").trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new IllegalArgumentException("reason must contain 1..500 characters");
        }
        return reason;
    }

    private static void requireUser(long userId) {
        if (userId <= 0) {
            throw failure(AccountLifecycleFailure.ACCOUNT_NOT_FOUND);
        }
    }

    private static LocalDateTime local(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static AccountLifecycleException failure(AccountLifecycleFailure failure) {
        return new AccountLifecycleException(failure);
    }

    private record Transitioned(AccountLifecycle lifecycle, String sessionRevocationState) {
    }
}
