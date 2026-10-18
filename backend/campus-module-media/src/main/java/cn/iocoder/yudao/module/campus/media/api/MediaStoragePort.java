package cn.iocoder.yudao.module.campus.media.api;

import java.io.InputStream;

public interface MediaStoragePort {

    StorageWriteResult putImmutable(String objectKey, InputStream content, long maxBytes);

    void delete(String objectKey);
}
