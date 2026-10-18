package cn.iocoder.yudao.module.campus.media.infrastructure;

import cn.iocoder.yudao.module.campus.media.api.MediaStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPrivateStorageAdapterTest {

    @TempDir
    Path root;

    @Test
    void writesOnceInsidePrivateRootAndNeverOverwrites() {
        LocalPrivateStorageAdapter storage = new LocalPrivateStorageAdapter(root.toString());

        assertThat(storage.putImmutable("private/test/object",
                new ByteArrayInputStream(new byte[]{1, 2, 3}), 3).bytesWritten()).isEqualTo(3);
        assertThatThrownBy(() -> storage.putImmutable("private/test/object",
                new ByteArrayInputStream(new byte[]{4}), 1))
                .isInstanceOfSatisfying(MediaStorageException.class,
                        error -> assertThat(error.reason()).isEqualTo(MediaStorageException.Reason.ALREADY_EXISTS));
    }

    @Test
    void rejectsTraversalAndDeletesPartialOversizeUpload() {
        LocalPrivateStorageAdapter storage = new LocalPrivateStorageAdapter(root.toString());

        assertThatThrownBy(() -> storage.putImmutable("../escape",
                new ByteArrayInputStream(new byte[]{1}), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.putImmutable("private/test/large",
                new ByteArrayInputStream(new byte[]{1, 2}), 1))
                .isInstanceOfSatisfying(MediaStorageException.class,
                        error -> assertThat(error.reason()).isEqualTo(MediaStorageException.Reason.TOO_LARGE));
        assertThat(root.resolve("private/test/large")).doesNotExist();
    }
}
