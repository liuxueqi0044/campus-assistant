package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAssetRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaBindingRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaBindingDO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaBindingServiceTest {

    private static final Instant FROM = Instant.parse("2026-09-18T00:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-10-18T00:00:00Z");

    @Test
    void bindsMultipleAssetsInAscendingOrderAndReplaysPersistedTimeWindow() {
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        MediaBindingRepository bindings = mock(MediaBindingRepository.class);
        MediaAssetDO first = asset(3, 1, 101, "MEMBERSHIP_VERIFICATION", "VALID");
        MediaAssetDO second = asset(7, 1, 101, "MEMBERSHIP_VERIFICATION", "VALID");
        when(assets.lock(3)).thenReturn(first);
        when(assets.lock(7)).thenReturn(second);
        AtomicReference<MediaBindingDO> three = new AtomicReference<>();
        AtomicReference<MediaBindingDO> seven = new AtomicReference<>();
        when(bindings.findByAssetId(3)).thenAnswer(ignored -> three.get());
        when(bindings.findByAssetId(7)).thenAnswer(ignored -> seven.get());
        doAnswer(invocation -> {
            MediaBindingDO row = invocation.getArgument(0);
            row.setId(row.getAssetId() + 100);
            if (row.getAssetId() == 3) three.set(row); else seven.set(row);
            return null;
        }).when(bindings).insert(any());
        MediaBindingService service = new MediaBindingService(assets, bindings);

        MediaBindingBatchResult created = service.bind(101, command(List.of(new MediaBindingAsset(7, 1),
                new MediaBindingAsset(3, 1)), FROM, UNTIL));
        MediaBindingBatchResult replay = service.bind(101, command(List.of(new MediaBindingAsset(3, 1),
                new MediaBindingAsset(7, 1)), FROM.plusSeconds(5), UNTIL.plusSeconds(5)));

        assertThat(created.bindings()).extracting(MediaBindingResult::assetId).containsExactly("3", "7");
        assertThat(replay.bindings()).extracting(MediaBindingResult::validFrom).containsExactly(FROM, FROM);
        verify(bindings, times(2)).insert(any());
    }

    @Test
    void rejectsCrossOwnerCrossPurposeAndNotReadyWithoutInserting() {
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        MediaBindingRepository bindings = mock(MediaBindingRepository.class);
        when(assets.lock(3)).thenReturn(asset(3, 1, 202, "MEMBERSHIP_VERIFICATION", "VALID"));
        MediaBindingService service = new MediaBindingService(assets, bindings);

        failure(() -> service.bind(101, command(List.of(new MediaBindingAsset(3, 1)), FROM, UNTIL)),
                CampusErrorCodes.MEDIA_NOT_OWNED);
        when(assets.lock(3)).thenReturn(asset(3, 1, 101, "OTHER", "VALID"));
        failure(() -> service.bind(101, command(List.of(new MediaBindingAsset(3, 1)), FROM, UNTIL)),
                CampusErrorCodes.MEDIA_PURPOSE_MISMATCH);
        when(assets.lock(3)).thenReturn(asset(3, 1, 101, "MEMBERSHIP_VERIFICATION", "UPLOADING"));
        failure(() -> service.bind(101, command(List.of(new MediaBindingAsset(3, 1)), FROM, UNTIL)),
                CampusErrorCodes.MEDIA_NOT_READY);
        verify(bindings, never()).insert(any());
    }

    @Test
    void duplicateUniqueConstraintBecomesAlreadyBoundWhenContextDiffers() {
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        MediaBindingRepository bindings = mock(MediaBindingRepository.class);
        MediaAssetDO asset = asset(3, 1, 101, "MEMBERSHIP_VERIFICATION", "VALID");
        when(assets.lock(3)).thenReturn(asset);
        when(bindings.findByAssetId(3)).thenReturn(null);
        org.springframework.dao.DuplicateKeyException duplicate = new org.springframework.dao.DuplicateKeyException("unique");
        org.mockito.Mockito.doThrow(duplicate).when(bindings).insert(any());
        MediaBindingService service = new MediaBindingService(assets, bindings);

        failure(() -> service.bind(101, command(List.of(new MediaBindingAsset(3, 1)), FROM, UNTIL)),
                CampusErrorCodes.MEDIA_ALREADY_BOUND);
    }

    private static MediaBindingCommand command(List<MediaBindingAsset> assets, Instant from, Instant until) {
        return new MediaBindingCommand("MEMBERSHIP_VERIFICATION", "MEMBERSHIP_VERIFICATION_PROOF", "proof-1",
                "school=2;campus=3;method=CARD", from, until, assets);
    }

    private static MediaAssetDO asset(long id, long generation, long owner, String purpose, String status) {
        MediaAssetDO asset = new MediaAssetDO();
        asset.setId(id);
        asset.setGeneration(generation);
        asset.setOwnerId(owner);
        asset.setPurpose(purpose);
        asset.setStatus(status);
        asset.setValidatedGeneration(generation);
        asset.setVersion(5L);
        return asset;
    }

    private static void failure(Runnable action, Object error) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CampusApiException.class,
                exception -> assertThat(exception.getSpec()).isEqualTo(error));
    }
}
