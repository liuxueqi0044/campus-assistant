package cn.iocoder.yudao.module.campus.media.controller.app.vo;

import cn.iocoder.yudao.module.campus.media.application.MediaDeliveryIssueResult;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record MediaDownloadGrantResponse(String deliveryToken, OffsetDateTime expiresAt,
                                         long assetGeneration, long assetVersion) {

    public static MediaDownloadGrantResponse from(MediaDeliveryIssueResult result) {
        return new MediaDownloadGrantResponse(result.deliveryToken(), result.expiresAt().atOffset(ZoneOffset.UTC),
                result.assetGeneration(), result.assetVersion());
    }
}
