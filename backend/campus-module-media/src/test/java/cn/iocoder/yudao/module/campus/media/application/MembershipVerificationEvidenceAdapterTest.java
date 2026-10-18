package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.application.port.VerificationProof;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAssetRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaBindingRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaBindingDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MembershipVerificationEvidenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");

    @AfterEach
    void clearTransactionMarker() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void newProofClaimsEveryAssetWithStableServerWindow() {
        MediaBindingService bindingService = mock(MediaBindingService.class);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        MediaBindingRepository bindings = mock(MediaBindingRepository.class);
        MediaAssetDO firstAsset = asset(11, 2);
        MediaAssetDO secondAsset = asset(12, 4);
        when(assets.find(11)).thenReturn(firstAsset);
        when(assets.find(12)).thenReturn(secondAsset);
        when(bindings.findByAssetIds(List.of(11L, 12L))).thenReturn(List.of());
        when(bindings.findByResource(org.mockito.ArgumentMatchers.eq("MEMBERSHIP_VERIFICATION_PROOF"),
                org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String resourceId = invocation.getArgument(1);
            String context = "v1|7|3|5|STUDENT_CARD|11,12";
            return List.of(binding(11, 2, resourceId, NOW, NOW.plus(Duration.ofDays(365)), context),
                    binding(12, 4, resourceId, NOW, NOW.plus(Duration.ofDays(365)), context));
        });
        TransactionSynchronizationManager.setActualTransactionActive(true);
        MembershipVerificationEvidenceAdapter adapter = adapter(bindingService, assets, bindings);

        VerificationProof proof = adapter.verify(7, 3, 5, "STUDENT_CARD", List.of("12", "11"));

        assertThat(proof.reference()).isNotBlank();
        assertThat(proof.validFrom()).isEqualTo(NOW);
        assertThat(proof.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(365)));
        ArgumentCaptor<MediaBindingCommand> command = ArgumentCaptor.forClass(MediaBindingCommand.class);
        verify(bindingService).bind(org.mockito.ArgumentMatchers.eq(7L), command.capture());
        assertThat(command.getValue().assets()).containsExactly(new MediaBindingAsset(11, 2, 12),
                new MediaBindingAsset(12, 4, 14));
        assertThat(command.getValue().resourceId()).isEqualTo(proof.reference());
    }

    @Test
    void replayReturnsPersistedWindowInsteadOfExtendingProof() {
        Instant original = NOW.minus(Duration.ofDays(2));
        Instant expiry = NOW.plus(Duration.ofDays(8));
        String resourceId = "proof-existing";
        MediaBindingService bindingService = mock(MediaBindingService.class);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        MediaBindingRepository bindings = mock(MediaBindingRepository.class);
        List<MediaBindingDO> rows = List.of(binding(11, 2, resourceId, original, expiry,
                "v1|7|3|5|STUDENT_CARD|11"));
        when(bindings.findByAssetIds(List.of(11L))).thenReturn(rows);
        when(bindings.findByResource("MEMBERSHIP_VERIFICATION_PROOF", resourceId)).thenReturn(rows);
        when(assets.find(11)).thenReturn(asset(11, 2));
        TransactionSynchronizationManager.setActualTransactionActive(true);

        VerificationProof proof = adapter(bindingService, assets, bindings)
                .verify(7, 3, 5, "STUDENT_CARD", List.of("11"));

        assertThat(proof.reference()).isEqualTo(resourceId);
        assertThat(proof.validFrom()).isEqualTo(original);
        assertThat(proof.expiresAt()).isEqualTo(expiry);
    }

    @Test
    void partialExistingBindingCannotBeTurnedIntoAProof() {
        MediaBindingService bindingService = mock(MediaBindingService.class);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        MediaBindingRepository bindings = mock(MediaBindingRepository.class);
        when(bindings.findByAssetIds(List.of(11L, 12L))).thenReturn(
                List.of(binding(11, 2, "occupied", NOW, NOW.plus(Duration.ofDays(10)),
                        "v1|7|3|5|STUDENT_CARD|11,12")));
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> adapter(bindingService, assets, bindings)
                .verify(7, 3, 5, "STUDENT_CARD", List.of("11", "12")))
                .isInstanceOfSatisfying(CampusApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(CampusErrorCodes.MEDIA_ALREADY_BOUND.getCode()));
    }

    @Test
    void proofCreationRequiresCallerTransaction() {
        MembershipVerificationEvidenceAdapter adapter = adapter(mock(MediaBindingService.class),
                mock(MediaAssetRepository.class), mock(MediaBindingRepository.class));

        assertThatThrownBy(() -> adapter.verify(7, 3, 5, "STUDENT_CARD", List.of("11")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static MembershipVerificationEvidenceAdapter adapter(MediaBindingService bindingService,
                                                                 MediaAssetRepository assets,
                                                                 MediaBindingRepository bindings) {
        return new MembershipVerificationEvidenceAdapter(bindingService, assets, bindings,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(365));
    }

    private static MediaAssetDO asset(long id, long generation) {
        MediaAssetDO asset = new MediaAssetDO();
        asset.setId(id);
        asset.setGeneration(generation);
        asset.setVersion(generation + 10);
        return asset;
    }

    private static MediaBindingDO binding(long assetId, long generation, String resourceId,
                                          Instant validFrom, Instant expiresAt, String context) {
        MediaBindingDO binding = new MediaBindingDO();
        binding.setId(assetId + 100);
        binding.setAssetId(assetId);
        binding.setAssetGeneration(generation);
        binding.setOwnerId(7L);
        binding.setPurpose("MEMBERSHIP_VERIFICATION");
        binding.setResourceType("MEMBERSHIP_VERIFICATION_PROOF");
        binding.setResourceId(resourceId);
        binding.setContextHash(MediaHashing.sha256(context));
        binding.setValidFrom(LocalDateTime.ofInstant(validFrom, ZoneOffset.UTC));
        binding.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        return binding;
    }
}
