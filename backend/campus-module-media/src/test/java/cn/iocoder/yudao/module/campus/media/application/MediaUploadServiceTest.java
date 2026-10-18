package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.media.api.MediaStoragePort;
import cn.iocoder.yudao.module.campus.media.api.StorageWriteResult;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAssetRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaUploadServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);
    private static final UploadTokenCodec CODEC = new UploadTokenCodec("test-only-signing-key-32-characters");

    @Test
    void writesExactDeclaredBytesForOwner() {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        MediaStoragePort storage = mock(MediaStoragePort.class);
        MediaAssetDO asset = asset();
        when(repository.find(77L)).thenReturn(asset);
        when(storage.putImmutable(anyString(), any(), anyLong())).thenReturn(new StorageWriteResult(3));
        MediaUploadService service = new MediaUploadService(repository, storage, CODEC, CLOCK);

        UploadContentResult result = service.upload(101L, 77L, CODEC.issue(asset), "image/png", 3,
                new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertThat(result.bytesWritten()).isEqualTo(3);
        verify(storage).putImmutable(eq(asset.getObjectKey()), any(), eq(3L));
    }

    @Test
    void crossOwnerOrExpiredTicketNeverCallsStorage() {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        MediaStoragePort storage = mock(MediaStoragePort.class);
        MediaAssetDO asset = asset();
        when(repository.find(77L)).thenReturn(asset);
        MediaUploadService service = new MediaUploadService(repository, storage, CODEC, CLOCK);

        assertFailure(() -> service.upload(202L, 77L, CODEC.issue(asset), "image/png", 3,
                new ByteArrayInputStream(new byte[3])), CampusErrorCodes.MEDIA_NOT_OWNED);
        asset.setTicketExpiresAt(LocalDateTime.parse("2026-09-16T23:59:59"));
        assertFailure(() -> service.upload(101L, 77L, CODEC.issue(asset), "image/png", 3,
                new ByteArrayInputStream(new byte[3])), CampusErrorCodes.MEDIA_NOT_READY);
        verify(storage, never()).putImmutable(anyString(), any(), anyLong());
    }

    private static void assertFailure(Runnable action, Object expectedSpec) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CampusApiException.class,
                error -> assertThat(error.getSpec()).isEqualTo(expectedSpec));
    }

    private static MediaAssetDO asset() {
        MediaAssetDO asset = new MediaAssetDO();
        asset.setId(77L);
        asset.setOwnerId(101L);
        asset.setPurpose("MEMBERSHIP_VERIFICATION");
        asset.setDeclaredMime("image/png");
        asset.setDeclaredSize(3L);
        asset.setObjectKey("private/membership_verification/g1/object");
        asset.setGeneration(1L);
        asset.setStatus("UPLOADING");
        asset.setTicketNonce("nonce");
        asset.setTicketExpiresAt(LocalDateTime.parse("2026-09-17T00:10:00"));
        return asset;
    }
}
