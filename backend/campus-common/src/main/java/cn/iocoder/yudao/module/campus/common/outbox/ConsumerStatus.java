package cn.iocoder.yudao.module.campus.common.outbox;

/** Per-consumer deduplication state. */
public enum ConsumerStatus { PROCESSING, FAILED, SUCCEEDED }
