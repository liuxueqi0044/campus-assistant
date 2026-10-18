package cn.iocoder.yudao.module.campus.media.application;

import java.time.Instant;
import java.util.List;

/**
 * Internal cross-domain binding command. {@code context} must be a server-built canonical value;
 * the media table stores only its SHA-256 digest.
 */
public record MediaBindingCommand(String purpose, String resourceType, String resourceId, String context,
                                  Instant validFrom, Instant expiresAt, List<MediaBindingAsset> assets) {
}
