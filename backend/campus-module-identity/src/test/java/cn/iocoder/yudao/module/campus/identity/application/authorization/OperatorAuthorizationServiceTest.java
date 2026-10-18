package cn.iocoder.yudao.module.campus.identity.application.authorization;

import cn.iocoder.yudao.module.campus.identity.api.AuthorizedSchoolScope;
import cn.iocoder.yudao.module.campus.identity.api.CampusScopeApi;
import cn.iocoder.yudao.module.campus.identity.api.OperatorAction;
import cn.iocoder.yudao.module.campus.identity.api.RequestedSchoolScope;
import cn.iocoder.yudao.module.campus.identity.application.MembershipVerificationException;
import cn.iocoder.yudao.module.campus.identity.application.MembershipVerificationFailure;
import cn.iocoder.yudao.module.campus.identity.application.port.OperatorAccountPort;
import cn.iocoder.yudao.module.campus.identity.infrastructure.OperatorGrantRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.OperatorGrantDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperatorAuthorizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");

    private final OperatorGrantRepository repository = mock(OperatorGrantRepository.class);
    private final OperatorAccountPort operatorAccountPort = mock(OperatorAccountPort.class);
    private final CampusScopeApi campusScopeApi = mock(CampusScopeApi.class);
    private OperatorAuthorizationService service;

    @BeforeEach
    void setUp() {
        when(operatorAccountPort.hasPermission(anyLong(), anyString())).thenReturn(true);
        when(repository.authorizationVersion(901)).thenReturn(4L);
        service = new OperatorAuthorizationService(repository, operatorAccountPort, campusScopeApi,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void exactCampusGrantAuthorizesServerOwnedCampus() {
        when(repository.findActiveGrants(901)).thenReturn(List.of(grant("CAMPUS", 1L, 11L,
                "[\"VERIFICATION_REVIEW\"]", NOW.plusSeconds(60))));

        AuthorizedSchoolScope result = service.authorize(901, OperatorAction.VERIFICATION_REVIEW,
                RequestedSchoolScope.campus(1, 11));

        assertThat(result.grantScopeType()).isEqualTo("CAMPUS");
        assertThat(result.schoolId()).isEqualTo(1);
        assertThat(result.campusId()).isEqualTo(11);
        assertThat(result.authorizationVersion()).isEqualTo(4);
    }

    @Test
    void campusGrantNeverWidensToAnotherCampusOrWholeSchool() {
        when(repository.findActiveGrants(901)).thenReturn(List.of(grant("CAMPUS", 1L, 11L,
                "[\"VERIFICATION_REVIEW\"]", NOW.plusSeconds(60))));

        assertFailure(() -> service.authorize(901, OperatorAction.VERIFICATION_REVIEW,
                RequestedSchoolScope.campus(1, 12)), OperatorAuthorizationFailure.SCOPE_DENIED);
        assertFailure(() -> service.authorize(901, OperatorAction.VERIFICATION_REVIEW,
                RequestedSchoolScope.school(1)), OperatorAuthorizationFailure.SCOPE_DENIED);
    }

    @Test
    void upstreamRbacDenialOverridesMatchingCampusGrant() {
        when(operatorAccountPort.hasPermission(901, OperatorAction.VERIFICATION_REVIEW.permission()))
                .thenReturn(false);
        when(repository.findActiveGrants(901)).thenReturn(List.of(grant("PLATFORM", null, null,
                "[\"VERIFICATION_REVIEW\"]", NOW.plusSeconds(60))));

        assertFailure(() -> service.authorize(901, OperatorAction.VERIFICATION_REVIEW,
                RequestedSchoolScope.campus(1, 11)), OperatorAuthorizationFailure.ACTION_DENIED);
    }

    @Test
    void exactExpiryBoundaryIsReportedAsExpired() {
        when(repository.findActiveGrants(901)).thenReturn(List.of(grant("SCHOOL", 1L, null,
                "[\"VERIFICATION_REVIEW\"]", NOW)));

        assertFailure(() -> service.authorize(901, OperatorAction.VERIFICATION_REVIEW,
                RequestedSchoolScope.campus(1, 11)), OperatorAuthorizationFailure.GRANT_EXPIRED);
    }

    @Test
    void revocationIsObservedByTheNextAuthorizationWithoutCacheFallback() {
        when(repository.findActiveGrants(901))
                .thenReturn(List.of(grant("SCHOOL", 1L, null,
                        "[\"VERIFICATION_REVIEW\"]", NOW.plusSeconds(60))))
                .thenReturn(List.of());

        service.authorize(901, OperatorAction.VERIFICATION_REVIEW, RequestedSchoolScope.campus(1, 11));

        assertFailure(() -> service.authorize(901, OperatorAction.VERIFICATION_REVIEW,
                RequestedSchoolScope.campus(1, 11)), OperatorAuthorizationFailure.SCOPE_DENIED);
    }

    @Test
    void unknownReviewActionMapsToFrozenReviewForbiddenFailure() {
        assertThatThrownBy(() -> service.assertAllowed(901, 1, 11, "UNKNOWN"))
                .isInstanceOfSatisfying(MembershipVerificationException.class,
                        error -> assertThat(error.failure())
                                .isEqualTo(MembershipVerificationFailure.VERIFICATION_REVIEW_FORBIDDEN));
    }

    @Test
    void unknownPersistedScopeTypeFailsClosedAsScopeDenied() {
        when(repository.findActiveGrants(901)).thenReturn(List.of(grant("UNKNOWN", 1L, null,
                "[\"VERIFICATION_REVIEW\"]", NOW.plusSeconds(60))));

        assertFailure(() -> service.authorize(901, OperatorAction.VERIFICATION_REVIEW,
                RequestedSchoolScope.school(1)), OperatorAuthorizationFailure.SCOPE_DENIED);
    }

    @Test
    void unknownPersistedActionFailsClosedAsActionDenied() {
        when(repository.findActiveGrants(901)).thenReturn(List.of(grant("SCHOOL", 1L, null,
                "[\"UNKNOWN_ACTION\"]", NOW.plusSeconds(60))));

        assertFailure(() -> service.authorize(901, OperatorAction.VERIFICATION_REVIEW,
                RequestedSchoolScope.school(1)), OperatorAuthorizationFailure.ACTION_DENIED);
    }

    @Test
    void malformedPersistedActionJsonFailsClosedAsActionDenied() {
        when(repository.findActiveGrants(901)).thenReturn(List.of(grant("SCHOOL", 1L, null,
                "not-json", NOW.plusSeconds(60))));

        assertFailure(() -> service.authorize(901, OperatorAction.VERIFICATION_REVIEW,
                RequestedSchoolScope.school(1)), OperatorAuthorizationFailure.ACTION_DENIED);
    }

    private static OperatorGrantDO grant(String scopeType, Long schoolId, Long campusId,
                                         String actions, Instant expiry) {
        OperatorGrantDO row = new OperatorGrantDO();
        row.setId(7L);
        row.setOperatorId(901L);
        row.setScopeType(scopeType);
        row.setSchoolId(schoolId);
        row.setCampusId(campusId);
        row.setActionsJson(actions);
        row.setStatus("ACTIVE");
        row.setValidFrom(local(NOW.minusSeconds(60)));
        row.setExpiresAt(local(expiry));
        row.setVersion(1L);
        return row;
    }

    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static void assertFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
                                      OperatorAuthorizationFailure expected) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(OperatorAuthorizationException.class,
                error -> assertThat(error.failure()).isEqualTo(expected));
    }
}
