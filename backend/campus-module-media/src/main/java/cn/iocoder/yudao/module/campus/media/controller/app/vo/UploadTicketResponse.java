package cn.iocoder.yudao.module.campus.media.controller.app.vo;

import cn.iocoder.yudao.module.campus.media.application.UploadTicketResult;

import java.time.Instant;
import java.util.Map;

public record UploadTicketResponse(String assetId, long generation, String uploadMethod, String uploadUrl,
                                   Instant expiresAt, Map<String, String> requiredHeaders) {

    public static UploadTicketResponse from(UploadTicketResult result) {
        return new UploadTicketResponse(result.assetId(), result.generation(), result.uploadMethod(),
                result.uploadUrl(), result.expiresAt(), result.requiredHeaders());
    }
}
