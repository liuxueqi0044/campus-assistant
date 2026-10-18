package cn.iocoder.yudao.module.campus.media.application;

/** An opaque asset reference plus the upload generation and version that the caller reviewed. */
public record MediaBindingAsset(long assetId, long generation, long expectedVersion) {

    /** Compatibility for trusted internal tests; public callers provide a positive version. */
    public MediaBindingAsset(long assetId, long generation) {
        this(assetId, generation, 0);
    }
}
