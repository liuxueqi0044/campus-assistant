package cn.iocoder.yudao.module.campus.common.outbox;

/** The only outcomes a consumer may report. */
public enum ConsumerExecutionResult { COMPLETED, RETRYABLE_FAILURE, PERMANENT_FAILURE }
