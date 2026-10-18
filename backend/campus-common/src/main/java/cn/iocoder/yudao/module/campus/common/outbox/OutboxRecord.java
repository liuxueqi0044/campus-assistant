package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;
import java.time.Instant;

/** Immutable projection of a durable event row. Payload remains internal and must not be exposed by management APIs. */
public record OutboxRecord(CampusEventEnvelope envelope, OutboxStatus status, Instant nextRunAt, int attempt,
                           int maxAttempts, String leaseOwner, Instant leaseUntil, long leaseToken,
                           String targetConsumer, String lastConsumer, String lastErrorCode, String lastErrorReason, long rowVersion,
                           String replayConsumer, String replayReason, Instant replayedAt,
                           Instant completedAt, Instant createdAt, Instant updatedAt) { }
