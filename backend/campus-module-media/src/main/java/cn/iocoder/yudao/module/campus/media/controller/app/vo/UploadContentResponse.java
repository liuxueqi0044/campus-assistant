package cn.iocoder.yudao.module.campus.media.controller.app.vo;

import cn.iocoder.yudao.module.campus.media.application.UploadContentResult;

public record UploadContentResponse(String assetId, long generation, long bytesWritten, String status) {

    public static UploadContentResponse from(UploadContentResult result) {
        return new UploadContentResponse(result.assetId(), result.generation(), result.bytesWritten(), result.status());
    }
}
