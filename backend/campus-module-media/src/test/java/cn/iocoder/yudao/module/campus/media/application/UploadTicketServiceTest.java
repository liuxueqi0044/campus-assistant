package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAssetRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadTicketServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsServerOwnedObjectAndReturnsReplayableBoundToken() {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        AtomicReference<MediaAssetDO> inserted = new AtomicReference<>();
        when(repository.findByTicket(anyLong(), anyString())).thenReturn(null);
        when(repository.countActiveTickets(anyLong(), anyString(), any())).thenReturn(0L);
        when(repository.lockByTicket(anyLong(), anyString())).thenAnswer(ignored -> inserted.get());
        doAnswer(invocation -> {
            MediaAssetDO row = invocation.getArgument(0);
            row.setId(77L);
            inserted.set(row);
            return null;
        }).when(repository).reserve(any());
        UploadTicketService service = service(repository);

        UploadTicketResult result = service.issue(101L, command("request-1", "card.png"));

        verify(repository).reserve(any());
        assertThat(result.assetId()).isEqualTo("77");
        assertThat(result.uploadUrl()).isEqualTo("/app-api/campus/v1/media/assets/77/content");
        assertThat(result.requiredHeaders()).containsKeys("Content-Type", "X-Upload-Token");
        assertThat(inserted.get().getObjectKey()).startsWith("private/membership_verification/g1/");
        assertThat(inserted.get().getObjectKey()).doesNotContain("card.png");
    }

    @Test
    void sameKeyWithDifferentBodyFailsWithoutNewAsset() {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        MediaAssetDO existing = existingAsset();
        when(repository.findByTicket(anyLong(), anyString())).thenReturn(existing);
        when(repository.lockByTicket(anyLong(), anyString())).thenReturn(existing);
        UploadTicketService service = service(repository);

        assertThatThrownBy(() -> service.issue(101L, command("request-1", "different.png")))
                .isInstanceOfSatisfying(CampusApiException.class,
                        error -> assertThat(error.getSpec()).isEqualTo(CampusErrorCodes.IDEMPOTENCY_CONFLICT));
        verify(repository, never()).reserve(any());
    }

    @Test
    void quotaRejectsNewTicketButDoesNotCreateAsset() {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        when(repository.findByTicket(anyLong(), anyString())).thenReturn(null);
        when(repository.countActiveTickets(anyLong(), anyString(), any()))
                .thenReturn((long) UploadTicketService.MAX_ACTIVE_TICKETS);
        UploadTicketService service = service(repository);

        assertThatThrownBy(() -> service.issue(101L, command("request-1", "card.png")))
                .isInstanceOfSatisfying(CampusApiException.class,
                        error -> assertThat(error.getSpec()).isEqualTo(CampusErrorCodes.UPLOAD_QUOTA_EXCEEDED));
        verify(repository, never()).reserve(any());
    }

    private static UploadTicketService service(MediaAssetRepository repository) {
        return new UploadTicketService(repository, new UploadPolicy(),
                new UploadTokenCodec("test-only-signing-key-32-characters"), CLOCK);
    }

    private static UploadTicketCommand command(String key, String fileName) {
        return new UploadTicketCommand("MEMBERSHIP_VERIFICATION", fileName, 3, "image/png", key);
    }

    private static MediaAssetDO existingAsset() {
        MediaAssetDO asset = new MediaAssetDO();
        asset.setId(77L);
        asset.setOwnerId(101L);
        asset.setPurpose("MEMBERSHIP_VERIFICATION");
        asset.setOriginalFilename("card.png");
        asset.setDeclaredMime("image/png");
        asset.setDeclaredSize(3L);
        asset.setObjectKey("private/membership_verification/g1/object");
        asset.setGeneration(1L);
        asset.setStatus("UPLOADING");
        asset.setIdempotencyKeyHash("unused");
        asset.setRequestHash("intentionally-different");
        asset.setTicketNonce("nonce");
        asset.setTicketExpiresAt(java.time.LocalDateTime.parse("2026-09-17T00:10:00"));
        asset.setVersion(1L);
        return asset;
    }
}
