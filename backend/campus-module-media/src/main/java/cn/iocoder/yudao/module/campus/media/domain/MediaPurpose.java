package cn.iocoder.yudao.module.campus.media.domain;

import java.util.Locale;
import java.util.Set;

public enum MediaPurpose {

    MEMBERSHIP_VERIFICATION(10 * 1024 * 1024L,
            Set.of("image/jpeg", "image/png", "application/pdf"));

    private final long maxBytes;
    private final Set<String> allowedMimeTypes;

    MediaPurpose(long maxBytes, Set<String> allowedMimeTypes) {
        this.maxBytes = maxBytes;
        this.allowedMimeTypes = allowedMimeTypes;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public boolean accepts(String mimeType) {
        return mimeType != null && allowedMimeTypes.contains(mimeType.toLowerCase(Locale.ROOT));
    }
}
