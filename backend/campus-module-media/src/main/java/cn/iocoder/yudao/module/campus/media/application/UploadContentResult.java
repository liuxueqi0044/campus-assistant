package cn.iocoder.yudao.module.campus.media.application;

public record UploadContentResult(String assetId, long generation, long bytesWritten, String status) {
}
