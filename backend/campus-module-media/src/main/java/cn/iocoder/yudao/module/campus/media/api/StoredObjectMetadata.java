package cn.iocoder.yudao.module.campus.media.api;

/** Metadata deliberately limited to what M02.02 needs to validate an immutable object. */
public record StoredObjectMetadata(long size) {

    public StoredObjectMetadata {
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }
}
