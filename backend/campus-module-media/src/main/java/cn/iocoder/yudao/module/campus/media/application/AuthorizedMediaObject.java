package cn.iocoder.yudao.module.campus.media.application;

/** Minimal server-side handoff to the proxy controller; it intentionally contains no URL or token. */
public record AuthorizedMediaObject(String objectKey, String actualMime, long actualSize, String originalFilename) {
}
