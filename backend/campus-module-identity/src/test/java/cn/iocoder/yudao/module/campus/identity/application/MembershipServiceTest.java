package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.module.campus.identity.api.CampusScopeApi;
import cn.iocoder.yudao.module.campus.identity.application.port.AccountStatusPort;
import cn.iocoder.yudao.module.campus.identity.infrastructure.MembershipVerificationRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.MembershipDO;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MembershipServiceTest {

    private static final Instant EXPIRY = Instant.parse("2026-10-01T00:00:00Z");
    private final MembershipVerificationRepository repository = mock(MembershipVerificationRepository.class);
    private final CampusScopeApi campusScopeApi = mock(CampusScopeApi.class);
    private final AccountStatusPort accountStatusPort = mock(AccountStatusPort.class);

    @Test
    void expiresAtBoundaryIsImmediatelyRejectedWithoutWaitingForAJob() {
        when(repository.findMembership(101, 1)).thenReturn(activeMembership());
        MembershipService service = new MembershipService(repository, campusScopeApi, accountStatusPort,
                Clock.fixed(EXPIRY, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.check(101, 1, 11, "CREATE_LISTING"))
                .isInstanceOfSatisfying(MembershipVerificationException.class,
                        error -> assertThat(error.failure())
                                .isEqualTo(MembershipVerificationFailure.MEMBERSHIP_EXPIRED));
    }

    @Test
    void activeMembershipReturnsAuthoritativeExpiryAndScopeVersion() {
        when(repository.findMembership(101, 1)).thenReturn(activeMembership());
        MembershipService service = new MembershipService(repository, campusScopeApi, accountStatusPort,
                Clock.fixed(EXPIRY.minusSeconds(1), ZoneOffset.UTC));

        assertThat(service.check(101, 1, 11, "CREATE_LISTING"))
                .satisfies(result -> {
                    assertThat(result.effective()).isTrue();
                    assertThat(result.expiresAt().toInstant()).isEqualTo(EXPIRY);
                    assertThat(result.scopeVersion()).isEqualTo(3);
                });
    }

    @Test
    void revokedMembershipIsDistinctFromMissingMembership() {
        MembershipDO revoked = activeMembership();
        revoked.setStatus("REVOKED");
        revoked.setRevokedAt(LocalDateTime.ofInstant(EXPIRY.minusSeconds(100), ZoneOffset.UTC));
        revoked.setRevokedBy(901L);
        revoked.setRevocationReason("revoked");
        when(repository.findMembership(101, 1)).thenReturn(revoked);
        MembershipService service = new MembershipService(repository, campusScopeApi, accountStatusPort,
                Clock.fixed(EXPIRY.minusSeconds(1), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.check(101, 1, 11, "CREATE_LISTING"))
                .isInstanceOfSatisfying(MembershipVerificationException.class,
                        error -> assertThat(error.failure())
                                .isEqualTo(MembershipVerificationFailure.MEMBERSHIP_REVOKED));
    }

    private static MembershipDO activeMembership() {
        MembershipDO row = new MembershipDO();
        row.setId(700L);
        row.setUserId(101L);
        row.setSchoolId(1L);
        row.setCampusId(11L);
        row.setSourceVerificationId(51L);
        row.setStatus("ACTIVE");
        row.setEffectiveFrom(LocalDateTime.ofInstant(EXPIRY.minusSeconds(3600), ZoneOffset.UTC));
        row.setExpiresAt(LocalDateTime.ofInstant(EXPIRY, ZoneOffset.UTC));
        row.setScopeVersion(3L);
        return row;
    }
}
