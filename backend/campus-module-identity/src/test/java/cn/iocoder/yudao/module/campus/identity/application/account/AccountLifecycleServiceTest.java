package cn.iocoder.yudao.module.campus.identity.application.account;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.identity.api.AccountLifecycleStatus;
import cn.iocoder.yudao.module.campus.identity.application.port.AccountObligation;
import cn.iocoder.yudao.module.campus.identity.application.port.AccountObligationPort;
import cn.iocoder.yudao.module.campus.identity.application.port.OperatorAccountPort;
import cn.iocoder.yudao.module.campus.identity.application.port.SessionRevocationPort;
import cn.iocoder.yudao.module.campus.identity.application.port.UpstreamMemberAccountPort;
import cn.iocoder.yudao.module.campus.identity.infrastructure.AccountLifecycleRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.AccountLifecycleDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.AccountLifecycleLogDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");

    private final AccountLifecycleRepository repository = mock(AccountLifecycleRepository.class);
    private final UpstreamMemberAccountPort upstream = mock(UpstreamMemberAccountPort.class);
    private final OperatorAccountPort operator = mock(OperatorAccountPort.class);
    private final AccountObligationPort obligations = mock(AccountObligationPort.class);
    private final SessionRevocationPort sessions = mock(SessionRevocationPort.class);
    private final SessionRevocationCompletionService completion = mock(SessionRevocationCompletionService.class);
    private AccountLifecycleService service;

    @BeforeEach
    void setUp() {
        when(operator.hasPermission(901, AccountLifecycleService.MANAGE_PERMISSION)).thenReturn(true);
        service = new AccountLifecycleService(repository, upstream, operator, obligations, sessions, completion,
                immediateTransactions(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void firstSuspensionCreatesExtensionAndLogThenRevokesEveryOldSession() {
        when(repository.lock(7)).thenReturn(null);

        AccountLifecycleResult result = service.changeRestriction(901,
                new ChangeAccountLifecycleCommand(7, AccountLifecycleStatus.SUSPENDED, "risk review", 0));

        assertThat(result.status()).isEqualTo(AccountLifecycleStatus.SUSPENDED);
        assertThat(result.authVersion()).isEqualTo(1);
        assertThat(result.version()).isEqualTo(1);
        ArgumentCaptor<AccountLifecycleDO> row = ArgumentCaptor.forClass(AccountLifecycleDO.class);
        verify(repository).insert(row.capture());
        assertThat(row.getValue().getSessionRevocationState()).isEqualTo("PENDING");
        assertThat(row.getValue().getStatus()).isEqualTo("SUSPENDED");
        ArgumentCaptor<AccountLifecycleLogDO> log = ArgumentCaptor.forClass(AccountLifecycleLogDO.class);
        verify(repository).appendLog(log.capture());
        assertThat(log.getValue().getFromStatus()).isEqualTo("IMPLICIT_ACTIVE");
        assertThat(log.getValue().getActorType()).isEqualTo("OPERATOR");
        verify(sessions).revokeAll(7);
        verify(completion).markComplete(7, 1);
    }

    @Test
    void suspendedLifecycleRejectsSensitiveCommandEvenIfOldTokenAuthenticatedUpstream() {
        when(repository.find(7)).thenReturn(row("SUSPENDED", 2, 2));

        assertThatThrownBy(() -> service.assertActive(7)).isInstanceOf(CampusApiException.class);
        verify(upstream).assertActive(7);
    }

    @Test
    void unsettledFundsBlockClosureWithoutWritingLifecycleOrDeletingAnything() {
        when(repository.find(7)).thenReturn(null);
        when(obligations.findBlockers(7)).thenReturn(List.of(
                new AccountObligation("UNSETTLED_FUNDS", 1, "SETTLE_FUNDS")));

        AccountClosureResult result = service.requestClosure(7,
                new RequestAccountClosureCommand("user request", 0));

        assertThat(result.status()).isEqualTo(AccountLifecycleStatus.ACTIVE);
        assertThat(result.blockers()).extracting(AccountObligation::category)
                .containsExactly("UNSETTLED_FUNDS");
        assertThat(result.nextAction()).isEqualTo("SETTLE_FUNDS");
        verify(repository, never()).insert(any());
        verify(repository, never()).appendLog(any());
        verify(sessions, never()).revokeAll(anyLong());
    }

    @Test
    void staleLifecycleVersionCannotOverwriteNewerRestriction() {
        when(repository.lock(7)).thenReturn(row("LIMITED", 2, 2));

        assertThatThrownBy(() -> service.changeRestriction(901,
                new ChangeAccountLifecycleCommand(7, AccountLifecycleStatus.SUSPENDED, "risk review", 1)))
                .isInstanceOfSatisfying(AccountLifecycleException.class,
                        error -> assertThat(error.failure()).isEqualTo(AccountLifecycleFailure.LIFECYCLE_CONFLICT));
        verify(repository, never()).appendLog(any());
    }

    @Test
    void revocationFailureLeavesDurablePendingStateForRetry() {
        when(repository.lock(7)).thenReturn(null);
        doThrow(new IllegalStateException("oauth unavailable")).when(sessions).revokeAll(7);

        AccountLifecycleResult result = service.changeRestriction(901,
                new ChangeAccountLifecycleCommand(7, AccountLifecycleStatus.LIMITED, "temporary limit", 0));

        assertThat(result.sessionRevocationState()).isEqualTo("PENDING");
        verify(completion, never()).markComplete(anyLong(), anyLong());
    }

    @Test
    void unknownPersistedStatusFailsClosedWithoutSideEffects() {
        when(repository.find(7)).thenReturn(row("FUTURE_STATUS", 1, 1));

        assertThatThrownBy(() -> service.snapshot(7))
                .isInstanceOfSatisfying(AccountLifecycleException.class,
                        error -> assertThat(error.failure())
                                .isEqualTo(AccountLifecycleFailure.ACCOUNT_ACTION_RESTRICTED));
        verify(repository, never()).insert(any());
        verify(repository, never()).appendLog(any());
        verify(sessions, never()).revokeAll(anyLong());
    }

    @Test
    void malformedPersistedVersionsFailClosedOnPermissionAndTransitionReads() {
        when(repository.find(7)).thenReturn(row("ACTIVE", -1, 1));

        assertThatThrownBy(() -> service.assertAllowed(7,
                cn.iocoder.yudao.module.campus.identity.api.AccountCapability.PRIVATE_RESOURCE_READ))
                .isInstanceOfSatisfying(AccountLifecycleException.class,
                        error -> assertThat(error.failure())
                                .isEqualTo(AccountLifecycleFailure.ACCOUNT_ACTION_RESTRICTED));

        when(repository.lock(7)).thenReturn(row("ACTIVE", 1, 0));
        assertThatThrownBy(() -> service.changeRestriction(901,
                new ChangeAccountLifecycleCommand(7, AccountLifecycleStatus.LIMITED, "temporary", 0)))
                .isInstanceOfSatisfying(AccountLifecycleException.class,
                        error -> assertThat(error.failure())
                                .isEqualTo(AccountLifecycleFailure.ACCOUNT_ACTION_RESTRICTED));
        verify(repository, never()).insert(any());
        verify(repository, never()).appendLog(any());
        verify(sessions, never()).revokeAll(anyLong());
    }

    @Test
    void malformedPersistedClosureShapeFailsClosedWithoutRevocation() {
        AccountLifecycleDO malformed = row("CLOSED", 2, 2);
        malformed.setClosureRequestedAt(java.time.LocalDateTime.of(2026, 9, 17, 12, 1));
        malformed.setClosedAt(java.time.LocalDateTime.of(2026, 9, 17, 12, 0));
        when(repository.find(7)).thenReturn(malformed);

        assertThatThrownBy(() -> service.assertAllowed(7,
                cn.iocoder.yudao.module.campus.identity.api.AccountCapability.PRIVATE_RESOURCE_READ))
                .isInstanceOfSatisfying(AccountLifecycleException.class,
                        error -> assertThat(error.failure())
                                .isEqualTo(AccountLifecycleFailure.ACCOUNT_ACTION_RESTRICTED));
        verify(repository, never()).insert(any());
        verify(repository, never()).appendLog(any());
        verify(sessions, never()).revokeAll(anyLong());
    }

    private static AccountLifecycleDO row(String status, long authVersion, long version) {
        AccountLifecycleDO row = new AccountLifecycleDO();
        row.setUserId(7L);
        row.setStatus(status);
        row.setAuthVersion(authVersion);
        row.setVersion(version);
        row.setStatusReason("fixture");
        row.setSessionRevocationState("COMPLETE");
        return row;
    }

    private static TransactionOperations immediateTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
    }
}
