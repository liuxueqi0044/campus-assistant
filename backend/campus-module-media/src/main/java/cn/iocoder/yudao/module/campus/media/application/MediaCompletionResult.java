package cn.iocoder.yudao.module.campus.media.application;

/** Stable completion view; validation failures are persisted as REJECTED for idempotent replay. */
public record MediaCompletionResult(String assetId, long generation, String status, long assetVersion) {
}
