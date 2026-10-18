package cn.iocoder.yudao.module.campus.media.application;

import java.time.Instant;
import java.util.Map;

public record UploadTicketResult(String assetId, long generation, String uploadMethod, String uploadUrl,
                                 Instant expiresAt, Map<String, String> requiredHeaders) {
}
