package cn.iocoder.yudao.module.campus.identity.domain.membership;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipTest {

    private static final Instant START = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant EXPIRY = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void becomesExpiredExactlyAtAuthoritativeExpiryWithoutStatusRewrite() {
        Membership membership = activeMembership();

        assertThat(membership.evaluate(11, Clock.fixed(EXPIRY.minusMillis(1), ZoneOffset.UTC)))
                .isEqualTo(MembershipEvaluation.EFFECTIVE);
        assertThat(membership.evaluate(11, Clock.fixed(EXPIRY, ZoneOffset.UTC)))
                .isEqualTo(MembershipEvaluation.EXPIRED);
    }

    @Test
    void browsingAnotherCampusDoesNotExpandTheCertifiedScope() {
        assertThat(activeMembership().evaluate(12, Clock.fixed(START.plusSeconds(1), ZoneOffset.UTC)))
                .isEqualTo(MembershipEvaluation.REQUIRED);
    }

    @Test
    void revocationWinsEvenBeforeTheExpiryTime() {
        Membership membership = new Membership(1, 101, 1, 11, MembershipStatus.REVOKED,
                START, EXPIRY, 2);

        assertThat(membership.evaluate(11, Clock.fixed(START.plusSeconds(1), ZoneOffset.UTC)))
                .isEqualTo(MembershipEvaluation.REVOKED);
    }

    private static Membership activeMembership() {
        return new Membership(1, 101, 1, 11, MembershipStatus.ACTIVE, START, EXPIRY, 1);
    }
}
