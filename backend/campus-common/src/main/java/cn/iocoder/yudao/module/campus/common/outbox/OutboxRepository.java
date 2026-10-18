package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence boundary. Claim is called inside a short, independent transaction. */
public interface OutboxRepository {
    void insertIfAbsent(CampusEventEnvelope event, int maxAttempts, Instant now);
    Optional<OutboxRecord> find(String eventId);
    Optional<ConsumerStatus> consumptionStatus(String eventId, String consumer);
    List<OutboxRecord> claim(String owner, int limit, Instant now, Instant leaseUntil);
    void assertLease(String eventId, long leaseToken, Instant now);
    ConsumerStatus startConsumer(String eventId, String consumer, long leaseToken, Instant now);
    void succeedConsumer(String eventId, String consumer, long leaseToken, Instant now);
    void failConsumer(String eventId, String consumer, long leaseToken, String errorCode, String reason, Instant now);
    void renew(String eventId, long leaseToken, Instant leaseUntil, Instant now);
    void retry(String eventId, long leaseToken, Instant nextRunAt, String consumer, String errorCode, String reason, Instant now);
    void deadLetter(String eventId, long leaseToken, String consumer, String errorCode, String reason, Instant now);
    void succeed(String eventId, long leaseToken, String consumer, Instant now);
    boolean replay(String eventId, String consumer, String reason, long expectedVersion, Instant now);
}
