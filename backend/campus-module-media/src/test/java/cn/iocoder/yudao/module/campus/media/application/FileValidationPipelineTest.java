package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.media.infrastructure.LocalPrivateStorageAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileValidationPipelineTest {

    @TempDir
    Path root;

    private final FileValidationPipeline pipeline = new FileValidationPipeline();

    @Test
    void validatesRealPngWithServerComputedHash() throws Exception {
        byte[] png = png();
        LocalPrivateStorageAdapter storage = stored("png", png);

        FileValidationPipeline.ValidationResult result = pipeline.validate(storage, "private/test/png", png.length,
                "image/png");

        assertThat(result.valid()).isTrue();
        assertThat(result.actualMime()).isEqualTo("image/png");
        assertThat(result.sha256()).hasSize(64);
    }

    @Test
    void rejectsFakeJpegAndTruncatedPng() {
        byte[] fakeJpeg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2};
        byte[] truncatedPng = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        assertThat(pipeline.validate(stored("fake-jpeg", fakeJpeg), "private/test/fake-jpeg", fakeJpeg.length,
                "image/jpeg").failure()).isEqualTo("IMAGE_DECODE_FAILED");
        assertThat(pipeline.validate(stored("truncated-png", truncatedPng), "private/test/truncated-png",
                truncatedPng.length, "image/png").failure()).isEqualTo("IMAGE_DECODE_FAILED");
    }

    @Test
    void rejectsActualLengthMismatchBeforeTrustingContent() {
        byte[] pdf = "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII);

        FileValidationPipeline.ValidationResult result = pipeline.validate(stored("pdf", pdf), "private/test/pdf",
                pdf.length + 1L, "application/pdf");

        assertThat(result.valid()).isFalse();
        assertThat(result.failure()).isEqualTo("LENGTH_MISMATCH");
        assertThat(result.actualSize()).isEqualTo(pdf.length);
    }

    private LocalPrivateStorageAdapter stored(String key, byte[] bytes) {
        LocalPrivateStorageAdapter storage = new LocalPrivateStorageAdapter(root.toString());
        storage.putImmutable("private/test/" + key, new ByteArrayInputStream(bytes), bytes.length);
        return storage;
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "png", output)).isTrue();
        return output.toByteArray();
    }
}
