package cn.iocoder.yudao.module.campus.media.application;

public record UploadTicketCommand(String purpose, String fileName, long size, String mime, String idempotencyKey) {
}
