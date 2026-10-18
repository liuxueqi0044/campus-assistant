package cn.iocoder.yudao.module.campus.identity.domain.account;

import cn.iocoder.yudao.module.campus.identity.api.AccountCapability;
import cn.iocoder.yudao.module.campus.identity.api.AccountLifecycleStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");

    @Test
    void limitedAndClosurePendingAllowRemediationButNotNewSensitiveActions() {
        AccountLifecycle limited = new AccountLifecycle(7, AccountLifecycleStatus.LIMITED,
                1, 1, null, null);
        AccountLifecycle pending = new AccountLifecycle(7, AccountLifecycleStatus.CLOSURE_PENDING,
                2, 2, NOW, null);

        assertThat(limited.allows(AccountCapability.NEW_SENSITIVE_ACTION)).isFalse();
        assertThat(limited.allows(AccountCapability.OWN_REMEDIATION)).isTrue();
        assertThat(pending.allows(AccountCapability.NEW_SENSITIVE_ACTION)).isFalse();
        assertThat(pending.allows(AccountCapability.PRIVATE_RESOURCE_READ)).isTrue();
    }

    @Test
    void suspendedAndClosedDenyEveryStudentCapability() {
        AccountLifecycle suspended = new AccountLifecycle(7, AccountLifecycleStatus.SUSPENDED,
                1, 1, null, null);
        AccountLifecycle closed = new AccountLifecycle(7, AccountLifecycleStatus.CLOSED,
                2, 2, NOW.minusSeconds(60), NOW);

        for (AccountCapability capability : AccountCapability.values()) {
            assertThat(suspended.allows(capability)).isFalse();
            assertThat(closed.allows(capability)).isFalse();
        }
    }

    @Test
    void transitionAdvancesBothVersionsAndClosedIsTerminal() {
        AccountLifecycle pending = AccountLifecycle.implicitActive(7)
                .transition(AccountLifecycleStatus.CLOSURE_PENDING, NOW);
        AccountLifecycle closed = pending.transition(AccountLifecycleStatus.CLOSED, NOW.plusSeconds(60));

        assertThat(pending.version()).isEqualTo(1);
        assertThat(pending.authVersion()).isEqualTo(1);
        assertThat(closed.version()).isEqualTo(2);
        assertThat(closed.closedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThatThrownBy(() -> closed.transition(AccountLifecycleStatus.ACTIVE, NOW.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);
    }
}
