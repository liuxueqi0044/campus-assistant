package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;
import java.util.*;

/** Immutable registry; several differently named consumers may subscribe to one type/version. */
public final class EventHandlerRegistry {
    private final Map<Key, List<OutboxConsumer>> handlers;
    public EventHandlerRegistry(Collection<? extends OutboxConsumer> consumers) {
        Map<Key, List<OutboxConsumer>> rows = new HashMap<>();
        for (OutboxConsumer consumer : Objects.requireNonNull(consumers, "consumers")) {
            OutboxConsumer value = Objects.requireNonNull(consumer, "consumer");
            Key key = new Key(value.eventType(), value.eventVersion());
            List<OutboxConsumer> list = rows.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (list.stream().anyMatch(other -> other.name().equals(value.name()))) {
                throw new IllegalArgumentException("duplicate outbox consumer " + value.name() + " for " + key);
            }
            list.add(value);
        }
        rows.replaceAll((key, value) -> List.copyOf(value));
        this.handlers = Map.copyOf(rows);
    }
    public List<OutboxConsumer> handlersFor(CampusEventEnvelope event) {
        return handlers.getOrDefault(new Key(event.getType(), event.getVersion()), List.of());
    }
    public Optional<OutboxConsumer> handlerFor(CampusEventEnvelope event, String name) {
        return handlersFor(event).stream().filter(value -> value.name().equals(name)).findFirst();
    }
    private record Key(String type, int version) { }
}
