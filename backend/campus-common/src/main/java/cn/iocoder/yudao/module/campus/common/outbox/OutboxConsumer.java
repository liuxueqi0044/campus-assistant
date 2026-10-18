package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;

/**
 * A named internal consumer. Implementations must use the current transaction for database effects.
 *
 * <p>External calls are at-least-once: use a stable business number and the provider's idempotency/query
 * mechanism. The outbox cannot, and does not claim to, provide exactly-once network side effects.</p>
 */
public interface OutboxConsumer {
    String name();
    String eventType();
    int eventVersion();
    ConsumerExecutionResult handle(CampusEventEnvelope event);
}
