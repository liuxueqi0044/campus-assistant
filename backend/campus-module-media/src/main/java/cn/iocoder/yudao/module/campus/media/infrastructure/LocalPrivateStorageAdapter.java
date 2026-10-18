package cn.iocoder.yudao.module.campus.media.infrastructure;

import cn.iocoder.yudao.module.campus.media.api.MediaStorageException;
import cn.iocoder.yudao.module.campus.media.api.MediaStoragePort;
import cn.iocoder.yudao.module.campus.media.api.StorageWriteResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "campus.media.storage", havingValue = "local", matchIfMissing = true)
public class LocalPrivateStorageAdapter implements MediaStoragePort {

    private final Path root;

    public LocalPrivateStorageAdapter(@Value("${campus.media.local-root:${java.io.tmpdir}/campus-assistant/private-media}")
                                      String root) {
        this.root = Path.of(Objects.requireNonNull(root, "root")).toAbsolutePath().normalize();
    }

    @Override
    public StorageWriteResult putImmutable(String objectKey, InputStream content, long maxBytes) {
        Objects.requireNonNull(content, "content");
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        Path target = resolve(objectKey);
        Path temporary = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".uploading");
        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) {
                throw new MediaStorageException(MediaStorageException.Reason.ALREADY_EXISTS,
                        "immutable media object already exists");
            }
            long written = copyLimited(content, temporary, maxBytes);
            moveImmutable(temporary, target);
            return new StorageWriteResult(written);
        } catch (MediaStorageException exception) {
            deleteQuietly(temporary);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(temporary);
            throw new MediaStorageException(MediaStorageException.Reason.UNAVAILABLE,
                    "private media storage is unavailable", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException exception) {
            throw new MediaStorageException(MediaStorageException.Reason.UNAVAILABLE,
                    "private media object could not be removed", exception);
        }
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("objectKey must be a normalized relative path");
        }
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("objectKey escapes private storage root");
        }
        return resolved;
    }

    private static long copyLimited(InputStream content, Path temporary, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long written = 0;
        try (var output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            int count;
            while ((count = content.read(buffer)) >= 0) {
                written += count;
                if (written > maxBytes) {
                    throw new MediaStorageException(MediaStorageException.Reason.TOO_LARGE,
                            "media object exceeds declared size");
                }
                output.write(buffer, 0, count);
            }
        }
        return written;
    }

    private static void moveImmutable(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        } catch (FileAlreadyExistsException exception) {
            throw new MediaStorageException(MediaStorageException.Reason.ALREADY_EXISTS,
                    "immutable media object already exists", exception);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The original storage error is more useful; cleanup is retried by M02.05.
        }
    }
}
