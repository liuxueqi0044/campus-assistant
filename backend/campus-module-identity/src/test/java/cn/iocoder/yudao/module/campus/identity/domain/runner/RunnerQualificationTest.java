package cn.iocoder.yudao.module.campus.identity.domain.runner;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class RunnerQualificationTest {
    private static final Instant START = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");

    @Test void approveSuspendRestoreAndAssertReturnIdentityVersion() {
        RunnerQualification q = pending().approve(START, END);
        assertThat(q.status()).isEqualTo(RunnerQualificationStatus.ACTIVE);
        assertThat(q.assertForAccept(7, 8, 9, NOW)).isEqualTo(new RunnerQualificationAcceptance(1, 2));
        RunnerQualification suspended = q.suspend();
        assertThatThrownBy(() -> suspended.assertForAccept(7, 8, 9, NOW)).isInstanceOf(RunnerQualificationAssertionException.class);
        q = suspended.restore(NOW);
        assertThat(q.version()).isEqualTo(4);
    }

    @Test void expiryIsClockDerivedAndRestoreCannotResurrect() {
        RunnerQualification suspended = pending().approve(START, END).suspend();
        assertThatThrownBy(() -> suspended.restore(END)).hasMessage("EXPIRED");
        RunnerQualification active = pending().approve(START, END);
        assertThatThrownBy(() -> active.assertForAccept(7, 8, 9, END)).hasMessage("EXPIRED");
    }

    @Test void scopeAndStatusesAreExplicitlyRejected() {
        RunnerQualification pending = pending();
        assertThatThrownBy(() -> pending.assertForAccept(7, 8, 9, NOW)).hasMessage("NOT_APPROVED");
        RunnerQualification q = pending.approve(START, END);
        assertThatThrownBy(() -> q.assertForAccept(99, 8, 9, NOW)).hasMessage("USER_SCOPE_DENIED");
        assertThatThrownBy(() -> q.assertForAccept(7, 99, 9, NOW)).hasMessage("SCHOOL_SCOPE_DENIED");
        assertThatThrownBy(() -> q.assertForAccept(7, 8, 99, NOW)).hasMessage("CAMPUS_SCOPE_DENIED");
    }

    @Test void suspendedAndRevokedStatesCannotAcceptAndFutureWindowIsDenied() {
        RunnerQualification q = pending().approve(START, END);
        assertThatThrownBy(() -> pending().approve(NOW.plusSeconds(60), END)
                .assertForAccept(7, 8, 9, NOW)).hasMessage("NOT_YET_EFFECTIVE");
        assertThatThrownBy(() -> q.suspend().assertForAccept(7, 8, 9, NOW))
                .hasMessage("SUSPENDED");
        assertThatThrownBy(() -> q.revoke().assertForAccept(7, 8, 9, NOW))
                .hasMessage("REVOKED");
    }

    @Test void illegalTransitionsAndWindowsAreRejected() {
        assertThatThrownBy(() -> pending().approve(END, START)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending().suspend()).isInstanceOf(IllegalStateException.class);
        RunnerQualification q = pending().approve(START, END).revoke();
        assertThatThrownBy(() -> q.restore(NOW)).isInstanceOf(IllegalStateException.class);
    }

    private static RunnerQualification pending() {
        return new RunnerQualification(1, 7, 8, 9, RunnerQualificationStatus.PENDING, null, null, 1);
    }
}
