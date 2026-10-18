package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.media.api.MediaStorageException;
import cn.iocoder.yudao.module.campus.media.api.MediaStoragePort;
import cn.iocoder.yudao.module.campus.media.infrastructure.LocalPrivateStorageAdapter;
import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAssetRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaCompletionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path root;

    @Test
    void rejectsFakeJpegAndReplaysItsPersistedTerminalState() {
        byte[] bytes = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0};
        LocalPrivateStorageAdapter storage = stored(bytes);
        AtomicReference<MediaAssetDO> state = new AtomicReference<>(asset(1, "UPLOADING", bytes.length, "image/jpeg"));
        MediaAssetRepository repository = statefulRepository(state);
        MediaCompletionService service = new MediaCompletionService(repository, storage, new FileValidationPipeline(), CLOCK);

        MediaCompletionResult first = service.complete(101, 77, 1);
        MediaCompletionResult replay = service.complete(101, 77, 1);

        assertThat(first.status()).isEqualTo("REJECTED");
        assertThat(replay).isEqualTo(first);
        assertThat(state.get().getValidationFailure()).isEqualTo("IMAGE_DECODE_FAILED");
    }

    @Test
    void validatesRealPngOnceAndReplaysTheSameValidTerminalState() throws Exception {
        byte[] png = png();
        LocalPrivateStorageAdapter storage = stored(png);
        AtomicReference<MediaAssetDO> state = new AtomicReference<>(asset(1, "UPLOADING", png.length, "image/png"));
        MediaAssetRepository repository = statefulRepository(state);
        MediaCompletionService service = new MediaCompletionService(repository, storage, new FileValidationPipeline(), CLOCK);

        MediaCompletionResult first = service.complete(101, 77, 1);
        MediaCompletionResult replay = service.complete(101, 77, 1);

        assertThat(first.status()).isEqualTo("VALID");
        assertThat(replay).isEqualTo(first);
        assertThat(state.get().getContentSha256()).hasSize(64);
    }

    @Test
    void oldGenerationCannotCommitAValidationResult() throws Exception {
        byte[] png = png();
        LocalPrivateStorageAdapter storage = stored(png);
        AtomicReference<MediaAssetDO> state = new AtomicReference<>(asset(1, "UPLOADING", png.length, "image/png"));
        MediaAssetRepository repository = statefulRepository(state);
        doAnswer(invocation -> {
            MediaAssetDO current = state.get();
            current.setGeneration(2L);
            current.setStatus("UPLOADING");
            current.setVersion(current.getVersion() + 1);
            return false;
        }).when(repository).submitValidation(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong(),
                anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.nullable(String.class));
        MediaCompletionService service = new MediaCompletionService(repository, storage, new FileValidationPipeline(), CLOCK);

        assertThatThrownBy(() -> service.complete(101, 77, 1))
                .isInstanceOfSatisfying(CampusApiException.class,
                        error -> assertThat(error.getSpec()).isEqualTo(CampusErrorCodes.MEDIA_NOT_READY));
        assertThat(state.get().getStatus()).isEqualTo("UPLOADING");
        assertThat(state.get().getValidatedGeneration()).isNull();
        verify(repository).submitValidation(eq(77L), eq(1L), eq(2L), eq("VALID"), eq("image/png"), eq((long) png.length),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void missingObjectStaysRetryableRatherThanBecomingRejected() {
        AtomicReference<MediaAssetDO> state = new AtomicReference<>(asset(1, "UPLOADING", 3, "image/png"));
        MediaAssetRepository repository = statefulRepository(state);
        MediaStoragePort storage = mock(MediaStoragePort.class);
        when(storage.stat("private/test/object")).thenThrow(new MediaStorageException(
                MediaStorageException.Reason.NOT_FOUND, "not uploaded"));
        MediaCompletionService service = new MediaCompletionService(repository, storage, new FileValidationPipeline(), CLOCK);

        assertThatThrownBy(() -> service.complete(101, 77, 1))
                .isInstanceOfSatisfying(CampusApiException.class,
                        error -> assertThat(error.getSpec()).isEqualTo(CampusErrorCodes.MEDIA_NOT_READY));
        assertThat(state.get().getStatus()).isEqualTo("VALIDATING");
        verify(repository, never()).submitValidation(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong(),
                anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.nullable(String.class));
    }

    private MediaAssetRepository statefulRepository(AtomicReference<MediaAssetDO> state) {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        when(repository.find(77L)).thenAnswer(ignored -> state.get());
        doAnswer(invocation -> {
            MediaAssetDO current = state.get();
            if (current.getGeneration().equals(invocation.getArgument(1)) && current.getVersion().equals(invocation.getArgument(2))
                    && "UPLOADING".equals(current.getStatus())) {
                current.setStatus("VALIDATING");
                current.setVersion(current.getVersion() + 1);
                return true;
            }
            return false;
        }).when(repository).beginValidation(anyLong(), anyLong(), anyLong());
        doAnswer(invocation -> {
            MediaAssetDO current = state.get();
            if (current.getGeneration().equals(invocation.getArgument(1)) && current.getVersion().equals(invocation.getArgument(2))
                    && "VALIDATING".equals(current.getStatus())) {
                current.setStatus(invocation.getArgument(3));
                current.setActualMime(invocation.getArgument(4));
                current.setActualSize(invocation.getArgument(5));
                current.setContentSha256(invocation.getArgument(6));
                current.setValidatedGeneration(invocation.getArgument(1));
                current.setValidatedAt(invocation.getArgument(7));
                current.setValidationFailure(invocation.getArgument(8));
                current.setVersion(current.getVersion() + 1);
                return true;
            }
            return false;
        }).when(repository).submitValidation(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong(),
                anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.nullable(String.class));
        return repository;
    }

    private LocalPrivateStorageAdapter stored(byte[] bytes) {
        LocalPrivateStorageAdapter storage = new LocalPrivateStorageAdapter(root.toString());
        storage.putImmutable("private/test/object", new ByteArrayInputStream(bytes), bytes.length);
        return storage;
    }

    private static MediaAssetDO asset(long generation, String status, long size, String mime) {
        MediaAssetDO asset = new MediaAssetDO();
        asset.setId(77L);
        asset.setOwnerId(101L);
        asset.setPurpose("MEMBERSHIP_VERIFICATION");
        asset.setObjectKey("private/test/object");
        asset.setGeneration(generation);
        asset.setDeclaredSize(size);
        asset.setDeclaredMime(mime);
        asset.setStatus(status);
        asset.setVersion(1L);
        return asset;
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
