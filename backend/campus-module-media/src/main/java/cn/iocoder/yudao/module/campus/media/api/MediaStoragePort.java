package cn.iocoder.yudao.module.campus.media.api;

import java.io.InputStream;

public interface MediaStoragePort {

    StorageWriteResult putImmutable(String objectKey, InputStream content, long maxBytes);

    /**
     * Reads metadata for one private object only. Implementations must not expose bucket listing.
     */
    StoredObjectMetadata stat(String objectKey);

    /**
     * Opens one private object only. The caller owns and must close the returned stream.
     */
    InputStream open(String objectKey);

    void delete(String objectKey);
}
