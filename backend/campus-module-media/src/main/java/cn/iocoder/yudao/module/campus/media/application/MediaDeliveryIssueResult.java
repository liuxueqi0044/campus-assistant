package cn.iocoder.yudao.module.campus.media.application;

import java.time.Instant;

/** Short-lived opaque capability for the later proxy endpoint. Never serialize this into an audit row. */
public record MediaDeliveryIssueResult(String deliveryToken, Instant expiresAt,
                                       long assetGeneration, long assetVersion) {
}
