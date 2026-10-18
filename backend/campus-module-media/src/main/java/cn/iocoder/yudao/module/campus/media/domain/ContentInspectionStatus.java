package cn.iocoder.yudao.module.campus.media.domain;

/** Versioned content inspection states; a provider outage never implies PASS. */
public enum ContentInspectionStatus {
    PENDING,
    PASS,
    REJECT,
    REVIEW_REQUIRED,
    ERROR
}
