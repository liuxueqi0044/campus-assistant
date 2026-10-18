package cn.iocoder.yudao.module.campus.media.controller.app.vo;

import cn.iocoder.yudao.module.campus.media.application.MediaCompletionResult;

public record MediaCompletionResponse(String assetId, long generation, String status, long assetVersion) {

    public static MediaCompletionResponse from(MediaCompletionResult result) {
        return new MediaCompletionResponse(result.assetId(), result.generation(), result.status(),
                result.assetVersion());
    }
}
