package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.media.api.MediaStorageException;
import cn.iocoder.yudao.module.campus.media.api.MediaStoragePort;
import cn.iocoder.yudao.module.campus.media.api.StoredObjectMetadata;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

/**
 * Validates bytes obtained from the private storage object, never client-provided filename or hash.
 */
@Component
public class FileValidationPipeline {

    static final long MAX_VALIDATION_BYTES = 16L * 1024 * 1024;
    static final long MAX_IMAGE_PIXELS = 40_000_000L;

    public ValidationResult validate(MediaStoragePort storage, String objectKey, long declaredSize,
                                     String declaredMime) {
        StoredObjectMetadata metadata = storage.stat(objectKey);
        long actualSize = metadata.size();
        if (actualSize != declaredSize) {
            return ValidationResult.rejected(actualSize, null, "LENGTH_MISMATCH");
        }
        if (actualSize <= 0 || actualSize > MAX_VALIDATION_BYTES) {
            return ValidationResult.rejected(actualSize, null, "VALIDATION_SIZE_LIMIT");
        }
        byte[] bytes = readPrivateBytes(storage, objectKey, actualSize);
        String sha256 = sha256(bytes);
        String actualMime = sniffMime(bytes);
        if (actualMime == null) {
            return ValidationResult.rejected(actualSize, sha256, "UNSUPPORTED_MAGIC");
        }
        if (!actualMime.equalsIgnoreCase(declaredMime)) {
            return ValidationResult.rejected(actualSize, sha256, "MIME_MISMATCH");
        }
        String failure = switch (actualMime) {
            case "image/jpeg", "image/png" -> decodeImage(bytes);
            case "application/pdf" -> validatePdf(bytes);
            default -> "UNSUPPORTED_MAGIC";
        };
        return failure == null
                ? ValidationResult.valid(actualMime, actualSize, sha256)
                : ValidationResult.rejected(actualMime, actualSize, sha256, failure);
    }

    private static byte[] readPrivateBytes(MediaStoragePort storage, String objectKey, long expectedSize) {
        try (InputStream input = storage.open(objectKey); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long copied = 0;
            for (int count; (count = input.read(buffer)) != -1;) {
                copied += count;
                if (copied > expectedSize || copied > MAX_VALIDATION_BYTES) {
                    throw new MediaStorageException(MediaStorageException.Reason.UNAVAILABLE,
                            "immutable object changed while being validated");
                }
                output.write(buffer, 0, count);
            }
            if (copied != expectedSize) {
                throw new MediaStorageException(MediaStorageException.Reason.UNAVAILABLE,
                        "immutable object length changed while being validated");
            }
            return output.toByteArray();
        } catch (MediaStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new MediaStorageException(MediaStorageException.Reason.UNAVAILABLE,
                    "private media object could not be read", exception);
        }
    }

    private static String sniffMime(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
                && bytes[3] == 'F' && bytes[4] == '-') {
            return "application/pdf";
        }
        return null;
    }

    private static String decodeImage(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return "IMAGE_DECODE_FAILED";
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return "IMAGE_DECODE_FAILED";
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || ((long) width * height) > MAX_IMAGE_PIXELS) {
                    return "IMAGE_PIXEL_LIMIT";
                }
                BufferedImage decoded = reader.read(0);
                return decoded == null ? "IMAGE_DECODE_FAILED" : null;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            return "IMAGE_DECODE_FAILED";
        }
    }

    private static String validatePdf(byte[] bytes) {
        int tailStart = Math.max(0, bytes.length - 8192);
        for (int index = tailStart; index <= bytes.length - 5; index++) {
            if (bytes[index] == '%' && bytes[index + 1] == '%' && bytes[index + 2] == 'E'
                    && bytes[index + 3] == 'O' && bytes[index + 4] == 'F') {
                return null;
            }
        }
        return "PDF_TRAILER_MISSING";
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be supported by the JRE", exception);
        }
    }

    public record ValidationResult(String actualMime, long actualSize, String sha256, String failure) {

        static ValidationResult valid(String actualMime, long actualSize, String sha256) {
            return new ValidationResult(actualMime, actualSize, sha256, null);
        }

        static ValidationResult rejected(long actualSize, String sha256, String failure) {
            return rejected(null, actualSize, sha256, failure);
        }

        static ValidationResult rejected(String actualMime, long actualSize, String sha256, String failure) {
            return new ValidationResult(actualMime, actualSize, sha256, failure);
        }

        public boolean valid() {
            return failure == null;
        }
    }
}
