package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.api.ActorRef;
import cn.iocoder.yudao.module.campus.identity.api.ActorType;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAuthorizationApi;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAuthorizationDecision;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAccessPurpose;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAccessAuditRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAssetRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaBindingRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaDeliveryGrantRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAccessAuditDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaBindingDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaDeliveryGrantDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DownloadAuthorizationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), ZoneOffset.UTC);
    private static final ActorRef U1 = new ActorRef(ActorType.MEMBER, 101);
    private static final ActorRef U2 = new ActorRef(ActorType.MEMBER, 202);
    private static final ActorRef OPERATOR = new ActorRef(ActorType.OPERATOR, 303);

    @Test
    void issueThenOpenAllowsTheSameActorAndOnlyReturnsProxyMetadata() {
        Fixture fixture = fixture();
        when(fixture.authorization.authorize(any(), any(), any(), any()))
                .thenReturn(ResourceAuthorizationDecision.allow(Set.of("OWNER")));
        MediaDeliveryGrantDO stored = new MediaDeliveryGrantDO();
        stored.setId(88L);
        fixture.grantRepository = captureGrant(fixture, stored);
        DownloadAuthorizationService service = fixture.service();

        MediaDeliveryIssueResult issued = service.issue(U1, 11L, null, null);
        when(fixture.grantRepository.findActiveByTokenHash(anyString(), any())).thenReturn(stored);
        AuthorizedMediaObject opened = service.authorizeOpen(U1, issued.deliveryToken());

        assertThat(issued.deliveryToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(issued.assetGeneration()).isEqualTo(1L);
        assertThat(issued.assetVersion()).isEqualTo(5L);
        assertThat(stored.getTokenHash()).isEqualTo(MediaHashing.sha256(issued.deliveryToken()))
                .isNotEqualTo(issued.deliveryToken());
        assertThat(opened).isEqualTo(new AuthorizedMediaObject("private/proof/11", "image/png", 123L, "card.png"));
        verify(fixture.audits, org.mockito.Mockito.times(2)).insert(any(MediaAccessAuditDO.class));
    }

    @Test
    void operatorOpenReauthorizesUsingTheNormalizedGrantCaseContext() {
        Fixture fixture = fixture();
        when(fixture.authorization.authorize(any(), any(), any(), any()))
                .thenReturn(ResourceAuthorizationDecision.allow(Set.of("CASE_ACCESS")));
        MediaDeliveryGrantDO stored = new MediaDeliveryGrantDO();
        stored.setId(77L);
        captureGrant(fixture, stored);
        DownloadAuthorizationService service = fixture.service();

        MediaDeliveryIssueResult issued = service.issue(OPERATOR, 11L, " case-9 ", " operator review ");
        when(fixture.grantRepository.findActiveByTokenHash(anyString(), any())).thenReturn(stored);
        service.authorizeOpen(OPERATOR, issued.deliveryToken());
        ArgumentCaptor<ResourceAccessPurpose> purpose = ArgumentCaptor.forClass(ResourceAccessPurpose.class);

        verify(fixture.authorization, org.mockito.Mockito.times(2)).authorize(any(), any(), any(), purpose.capture());
        assertThat(stored.getCaseReference()).isEqualTo("case-9");
        assertThat(stored.getReason()).isEqualTo("operator review");
        assertThat(purpose.getAllValues()).extracting(ResourceAccessPurpose::caseReference)
                .containsExactly("case-9", "case-9");
        assertThat(purpose.getAllValues()).extracting(ResourceAccessPurpose::reason)
                .containsExactly("operator review", "operator review");
    }

    @Test
    void policyDenyWritesDenyAuditAndNeverCreatesGrant() {
        Fixture fixture = fixture();
        when(fixture.authorization.authorize(any(), any(), any(), any())).thenReturn(ResourceAuthorizationDecision.deny());
        DownloadAuthorizationService service = fixture.service();

        failure(() -> service.issue(U1, 11L, null, null), CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE);

        verify(fixture.deniedAudits).appendDenied(any(MediaAccessAuditDO.class));
        verify(fixture.grantRepository, never()).insert(any());
    }

    @Test
    void tokenCannotBeReplayedByAnotherActor() {
        Fixture fixture = fixture();
        MediaDeliveryGrantDO grant = grant(1, 5, 77);
        when(fixture.grantRepository.findActiveByTokenHash(anyString(), any())).thenReturn(grant);
        DownloadAuthorizationService service = fixture.service();

        failure(() -> service.authorizeOpen(U2, token()), CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE);

        verify(fixture.authorization, never()).authorize(any(), any(), any(), any());
        verify(fixture.deniedAudits).appendDenied(any(MediaAccessAuditDO.class));
    }

    @Test
    void openDenyUsesTheOpenAction() {
        Fixture fixture = fixture();
        when(fixture.grantRepository.findActiveByTokenHash(anyString(), any())).thenReturn(grant(1, 5, 77));
        when(fixture.authorization.authorize(any(), any(), any(), any())).thenReturn(ResourceAuthorizationDecision.deny());
        ArgumentCaptor<MediaAccessAuditDO> audit = ArgumentCaptor.forClass(MediaAccessAuditDO.class);

        failure(() -> fixture.service().authorizeOpen(U1, token()), CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE);

        verify(fixture.deniedAudits).appendDenied(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("OPEN_AUTHORIZED");
        assertThat(audit.getValue().getDecision()).isEqualTo("DENY");
    }

    @Test
    void crossOperatorTokenReplayUsesServerGeneratedAuditContext() {
        Fixture fixture = fixture();
        MediaDeliveryGrantDO grant = grant(1, 5, 77);
        grant.setActorType(ActorType.OPERATOR.name());
        grant.setActorId(OPERATOR.id());
        grant.setCaseReference("case-1");
        grant.setReason("original context");
        when(fixture.grantRepository.findActiveByTokenHash(anyString(), any())).thenReturn(grant);
        ArgumentCaptor<MediaAccessAuditDO> audit = ArgumentCaptor.forClass(MediaAccessAuditDO.class);

        failure(() -> fixture.service().authorizeOpen(new ActorRef(ActorType.OPERATOR, 404), token()),
                CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE);

        verify(fixture.deniedAudits).appendDenied(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("OPEN_AUTHORIZED");
        assertThat(audit.getValue().getCaseReference()).isEqualTo("DELIVERY_TOKEN_REPLAY");
        assertThat(audit.getValue().getReason()).isEqualTo("actor mismatch");
    }

    @Test
    void unknownAssetWritesDenyWithOnlyRequestedAssetId() {
        Fixture fixture = fixture();
        when(fixture.assets.find(99L)).thenReturn(null);
        ArgumentCaptor<MediaAccessAuditDO> audit = ArgumentCaptor.forClass(MediaAccessAuditDO.class);

        failure(() -> fixture.service().issue(U1, 99L, null, null), CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE);

        verify(fixture.deniedAudits).appendDenied(audit.capture());
        assertThat(audit.getValue().getAssetId()).isEqualTo(99L);
        assertThat(audit.getValue().getAssetGeneration()).isNull();
        assertThat(audit.getValue().getAssetVersion()).isNull();
        assertThat(audit.getValue().getBindingId()).isNull();
    }

    @Test
    void memberCannotSupplyCaseContextAndSystemNeverReachesStorageOrAudit() {
        Fixture fixture = fixture();

        failure(() -> fixture.service().issue(U1, 11L, "case-1", "reason"),
                CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE);
        failure(() -> fixture.service().issue(new ActorRef(ActorType.SYSTEM, 1), 11L, null, null),
                CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE);

        verify(fixture.assets, never()).find(11L);
        verify(fixture.deniedAudits, never()).appendDenied(any());
        verify(fixture.grantRepository, never()).insert(any());
    }

    @Test
    void expiredGrantIsDeniedBeforeAnyResourceLookup() {
        Fixture fixture = fixture();
        when(fixture.grantRepository.findActiveByTokenHash(anyString(), any())).thenReturn(null);
        DownloadAuthorizationService service = fixture.service();

        failure(() -> service.authorizeOpen(U1, token()), CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE);

        verify(fixture.assets, never()).find(11L);
    }

    @Test
    void invalidAndUnknownTokensWriteAnonymousOpenDenialsWithoutResourceLookup() {
        Fixture fixture = fixture();
        when(fixture.grantRepository.findActiveByTokenHash(anyString(), any())).thenReturn(null);
        ArgumentCaptor<MediaAccessAuditDO> audit = ArgumentCaptor.forClass(MediaAccessAuditDO.class);

        failure(() -> fixture.service().authorizeOpen(U1, "invalid"), CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE);
        failure(() -> fixture.service().authorizeOpen(U1, token()), CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE);

        verify(fixture.deniedAudits, org.mockito.Mockito.times(2)).appendDenied(audit.capture());
        assertThat(audit.getAllValues()).allSatisfy(row -> {
            assertThat(row.getAction()).isEqualTo("OPEN_AUTHORIZED");
            assertThat(row.getDecision()).isEqualTo("DENY");
            assertThat(row.getAssetId()).isNull();
            assertThat(row.getAssetGeneration()).isNull();
            assertThat(row.getAssetVersion()).isNull();
            assertThat(row.getBindingId()).isNull();
            assertThat(row.getResourceType()).isNull();
            assertThat(row.getResourceId()).isNull();
        });
        verify(fixture.assets, never()).find(11L);
        verify(fixture.authorization, never()).authorize(any(), any(), any(), any());
    }

    @Test
    void generationOrVersionChangeInvalidatesGrantAndExpiredOrReplacedBindingDoesToo() {
        Fixture fixture = fixture();
        when(fixture.authorization.authorize(any(), any(), any(), any()))
                .thenReturn(ResourceAuthorizationDecision.allow(Set.of("OWNER")));
        MediaDeliveryGrantDO grant = grant(1, 5, 77);
        when(fixture.grantRepository.findActiveByTokenHash(anyString(), any())).thenReturn(grant);
        fixture.asset.setVersion(6L);
        DownloadAuthorizationService service = fixture.service();

        failure(() -> service.authorizeOpen(U1, token()), CampusErrorCodes.MEDIA_UNAVAILABLE);
        fixture.asset.setVersion(5L);
        fixture.asset.setGeneration(2L);
        fixture.asset.setValidatedGeneration(2L);
        failure(() -> service.authorizeOpen(U1, token()), CampusErrorCodes.MEDIA_UNAVAILABLE);
        fixture.asset.setGeneration(1L);
        fixture.asset.setValidatedGeneration(1L);
        fixture.binding.setExpiresAt(LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
        failure(() -> service.authorizeOpen(U1, token()), CampusErrorCodes.MEDIA_UNAVAILABLE);
        fixture.binding.setExpiresAt(LocalDateTime.ofInstant(CLOCK.instant().plusSeconds(60), ZoneOffset.UTC));
        fixture.binding.setId(78L);
        failure(() -> service.authorizeOpen(U1, token()), CampusErrorCodes.MEDIA_UNAVAILABLE);
        verify(fixture.deniedAudits, org.mockito.Mockito.times(4)).appendDenied(any(MediaAccessAuditDO.class));
    }

    @Test
    void corruptedValidatedFactsCannotBeIssuedAsPrivateProofs() {
        Fixture fixture = fixture();
        when(fixture.authorization.authorize(any(), any(), any(), any()))
                .thenReturn(ResourceAuthorizationDecision.allow(Set.of("OWNER")));
        DownloadAuthorizationService service = fixture.service();

        fixture.asset.setActualSize(0L);
        failure(() -> service.issue(U1, 11L, null, null), CampusErrorCodes.MEDIA_UNAVAILABLE);
        fixture.asset.setActualSize(123L);
        fixture.asset.setActualMime("text/plain");
        failure(() -> service.issue(U1, 11L, null, null), CampusErrorCodes.MEDIA_UNAVAILABLE);
        fixture.asset.setActualMime("image/png");
        fixture.asset.setContentSha256("not-a-sha256");
        failure(() -> service.issue(U1, 11L, null, null), CampusErrorCodes.MEDIA_UNAVAILABLE);
        fixture.asset.setContentSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        fixture.binding.setPurpose("CHAT_ATTACHMENT");
        failure(() -> service.issue(U1, 11L, null, null), CampusErrorCodes.MEDIA_UNAVAILABLE);

        verify(fixture.grantRepository, never()).insert(any());
    }

    @Test
    void auditFailurePreventsIssueAndPreventsOpenObjectReturn() {
        Fixture issue = fixture();
        when(issue.authorization.authorize(any(), any(), any(), any()))
                .thenReturn(ResourceAuthorizationDecision.allow(Set.of("OWNER")));
        doThrow(new IllegalStateException("audit offline")).when(issue.audits).insert(any());
        assertThatThrownBy(() -> issue.service().issue(U1, 11L, null, null)).isInstanceOf(IllegalStateException.class);
        verify(issue.grantRepository, never()).insert(any());

        Fixture open = fixture();
        when(open.authorization.authorize(any(), any(), any(), any()))
                .thenReturn(ResourceAuthorizationDecision.allow(Set.of("OWNER")));
        when(open.grantRepository.findActiveByTokenHash(anyString(), any())).thenReturn(grant(1, 5, 77));
        doThrow(new IllegalStateException("audit offline")).when(open.audits).insert(any());
        assertThatThrownBy(() -> open.service().authorizeOpen(U1, token())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void auditModelCannotCarryTokenHashOrStorageAddress() {
        assertThat(Arrays.stream(MediaAccessAuditDO.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("token", "tokenHash", "objectKey", "url", "content");
    }

    @Test
    void auditLabelsArePersistedAsStableSortedJson() {
        Fixture fixture = fixture();
        when(fixture.authorization.authorize(any(), any(), any(), any()))
                .thenReturn(ResourceAuthorizationDecision.allow(Set.of("ZETA", "ALPHA")));
        ArgumentCaptor<MediaAccessAuditDO> audit = ArgumentCaptor.forClass(MediaAccessAuditDO.class);

        fixture.service().issue(U1, 11L, null, null);

        verify(fixture.audits).insert(audit.capture());
        assertThat(audit.getValue().getAuditLabelsJson()).isEqualTo("[\"ALPHA\",\"ZETA\"]");
    }

    private static Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.assets = mock(MediaAssetRepository.class);
        fixture.bindings = mock(MediaBindingRepository.class);
        fixture.grantRepository = mock(MediaDeliveryGrantRepository.class);
        fixture.audits = mock(MediaAccessAuditRepository.class);
        fixture.deniedAudits = mock(MediaAccessAuditWriter.class);
        fixture.authorization = mock(ResourceAuthorizationApi.class);
        fixture.asset = asset();
        fixture.binding = binding();
        when(fixture.assets.find(11L)).thenReturn(fixture.asset);
        when(fixture.bindings.findByAssetId(11L)).thenReturn(fixture.binding);
        return fixture;
    }

    private static MediaDeliveryGrantRepository captureGrant(Fixture fixture, MediaDeliveryGrantDO target) {
        org.mockito.Mockito.doAnswer(invocation -> {
            MediaDeliveryGrantDO written = invocation.getArgument(0);
            target.setTokenHash(written.getTokenHash());
            target.setActorType(written.getActorType());
            target.setActorId(written.getActorId());
            target.setAssetId(written.getAssetId());
            target.setAssetGeneration(written.getAssetGeneration());
            target.setAssetVersion(written.getAssetVersion());
            target.setBindingId(written.getBindingId());
            target.setCaseReference(written.getCaseReference());
            target.setReason(written.getReason());
            target.setExpiresAt(written.getExpiresAt());
            target.setVersion(written.getVersion());
            return null;
        }).when(fixture.grantRepository).insert(any());
        return fixture.grantRepository;
    }

    private static MediaDeliveryGrantDO grant(long generation, long version, long bindingId) {
        MediaDeliveryGrantDO grant = new MediaDeliveryGrantDO();
        grant.setTokenHash(MediaHashing.sha256(token()));
        grant.setActorType(ActorType.MEMBER.name());
        grant.setActorId(U1.id());
        grant.setAssetId(11L);
        grant.setAssetGeneration(generation);
        grant.setAssetVersion(version);
        grant.setBindingId(bindingId);
        grant.setExpiresAt(LocalDateTime.ofInstant(CLOCK.instant().plusSeconds(60), ZoneOffset.UTC));
        grant.setVersion(1L);
        return grant;
    }

    private static MediaAssetDO asset() {
        MediaAssetDO asset = new MediaAssetDO();
        asset.setId(11L);
        asset.setObjectKey("private/proof/11");
        asset.setOriginalFilename("card.png");
        asset.setPurpose("MEMBERSHIP_VERIFICATION");
        asset.setGeneration(1L);
        asset.setValidatedGeneration(1L);
        asset.setVersion(5L);
        asset.setStatus("VALID");
        asset.setActualMime("image/png");
        asset.setActualSize(123L);
        asset.setContentSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        return asset;
    }

    private static MediaBindingDO binding() {
        MediaBindingDO binding = new MediaBindingDO();
        binding.setId(77L);
        binding.setAssetId(11L);
        binding.setAssetGeneration(1L);
        binding.setPurpose("MEMBERSHIP_VERIFICATION");
        binding.setResourceType("MEMBERSHIP_VERIFICATION_PROOF");
        binding.setResourceId("proof-1");
        binding.setValidFrom(LocalDateTime.ofInstant(CLOCK.instant().minusSeconds(1), ZoneOffset.UTC));
        binding.setExpiresAt(LocalDateTime.ofInstant(CLOCK.instant().plusSeconds(60), ZoneOffset.UTC));
        binding.setVersion(1L);
        return binding;
    }

    private static String token() {
        return "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmno123456789_-".substring(0, 43);
    }

    private static void failure(Runnable action, Object error) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CampusApiException.class,
                exception -> {
                    if (error != null) assertThat(exception.getSpec()).isEqualTo(error);
                });
    }

    private static final class Fixture {
        private MediaAssetRepository assets;
        private MediaBindingRepository bindings;
        private MediaDeliveryGrantRepository grantRepository;
        private MediaAccessAuditRepository audits;
        private MediaAccessAuditWriter deniedAudits;
        private ResourceAuthorizationApi authorization;
        private MediaAssetDO asset;
        private MediaBindingDO binding;

        private DownloadAuthorizationService service() {
            return new DownloadAuthorizationService(assets, bindings, grantRepository, audits, deniedAudits,
                    authorization, new ObjectMapper(), CLOCK, Duration.ofSeconds(60));
        }
    }
}
