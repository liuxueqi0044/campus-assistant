package cn.iocoder.yudao.module.campus.identity.domain.verification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationAttemptTest {

    @Test
    void pendingAttemptCanBeApprovedAndThenRevokedWithMonotonicVersions() {
        VerificationAttempt pending = pending();

        VerificationAttempt approved = pending.transition(VerificationAction.APPROVE, 1);
        VerificationAttempt revoked = approved.transition(VerificationAction.REVOKE, 2);

        assertThat(approved.status()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(revoked.status()).isEqualTo(VerificationStatus.REVOKED);
        assertThat(revoked.version()).isEqualTo(3);
    }

    @Test
    void staleReviewerCannotOverwriteTheWinningDecision() {
        VerificationAttempt approved = pending().transition(VerificationAction.APPROVE, 1);

        assertThatThrownBy(() -> approved.transition(VerificationAction.REJECT, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version conflict");
    }

    @Test
    void rejectedAttemptIsTerminalAndDoesNotBecomeApprovedOnReplay() {
        VerificationAttempt rejected = pending().transition(VerificationAction.REJECT, 1);

        assertThatThrownBy(() -> rejected.transition(VerificationAction.APPROVE, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending");
    }

    private static VerificationAttempt pending() {
        return new VerificationAttempt(51, 101, 1, 11, VerificationStatus.PENDING, 1);
    }
}
