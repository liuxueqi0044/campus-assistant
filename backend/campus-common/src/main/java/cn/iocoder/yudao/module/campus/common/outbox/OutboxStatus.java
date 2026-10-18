package cn.iocoder.yudao.module.campus.common.outbox;

/** Durable delivery state. PENDING also represents a delayed retry. */
public enum OutboxStatus { PENDING, PROCESSING, SUCCEEDED, DEAD_LETTER }
