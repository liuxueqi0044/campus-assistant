package cn.iocoder.yudao.module.campus.identity.domain.authorization;

import cn.iocoder.yudao.module.campus.identity.api.OperatorAction;
import cn.iocoder.yudao.module.campus.identity.api.RequestedSchoolScope;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorGrantTest {

    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void campusGrantOnlyCoversItsExactCampus() {
        OperatorGrant grant = grant(OperatorScopeType.CAMPUS, 1L, 11L, NOW.plusSeconds(60));

        assertThat(grant.isEffectiveFor(OperatorAction.VERIFICATION_REVIEW,
                RequestedSchoolScope.campus(1, 11), CLOCK)).isTrue();
        assertThat(grant.covers(RequestedSchoolScope.campus(1, 12))).isFalse();
        assertThat(grant.covers(RequestedSchoolScope.school(1))).isFalse();
    }

    @Test
    void schoolAndExplicitPlatformScopesFollowHierarchy() {
        OperatorGrant school = grant(OperatorScopeType.SCHOOL, 1L, null, NOW.plusSeconds(60));
        OperatorGrant platform = grant(OperatorScopeType.PLATFORM, null, null, NOW.plusSeconds(60));

        assertThat(school.covers(RequestedSchoolScope.campus(1, 12))).isTrue();
        assertThat(school.covers(RequestedSchoolScope.campus(2, 21))).isFalse();
        assertThat(platform.covers(RequestedSchoolScope.campus(2, 21))).isTrue();
    }

    @Test
    void exactExpiryBoundaryIsInvalid() {
        OperatorGrant grant = grant(OperatorScopeType.SCHOOL, 1L, null, NOW);

        assertThat(grant.isCurrent(CLOCK)).isFalse();
        assertThat(grant.isEffectiveFor(OperatorAction.VERIFICATION_REVIEW,
                RequestedSchoolScope.campus(1, 11), CLOCK)).isFalse();
    }

    @Test
    void nullScopeNeverImplicitlyMeansPlatform() {
        assertThatThrownBy(() -> grant(OperatorScopeType.SCHOOL, null, null, NOW.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OperatorGrant grant(OperatorScopeType type, Long schoolId, Long campusId, Instant expiry) {
        return new OperatorGrant(7, 901, type, schoolId, campusId,
                Set.of(OperatorAction.VERIFICATION_REVIEW), OperatorGrantStatus.ACTIVE,
                NOW.minusSeconds(60), expiry, 1);
    }
}
