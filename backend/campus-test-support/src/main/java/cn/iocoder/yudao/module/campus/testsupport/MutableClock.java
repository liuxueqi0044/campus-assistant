package cn.iocoder.yudao.module.campus.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe clock for deterministic tests. */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> current;
    private final ZoneId zone;

    public MutableClock(Instant initialInstant, ZoneId zone) {
        this(new AtomicReference<>(Objects.requireNonNull(initialInstant, "initialInstant")),
                Objects.requireNonNull(zone, "zone"));
    }

    private MutableClock(AtomicReference<Instant> current, ZoneId zone) {
        this.current = current;
        this.zone = zone;
    }

    public static MutableClock at(Instant initialInstant) {
        return new MutableClock(initialInstant, ZoneId.of("UTC"));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public MutableClock withZone(ZoneId zone) {
        return new MutableClock(current, Objects.requireNonNull(zone, "zone"));
    }

    @Override
    public Instant instant() {
        return current.get();
    }

    public Instant set(Instant instant) {
        return current.getAndSet(Objects.requireNonNull(instant, "instant"));
    }

    public Instant advance(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        return current.updateAndGet(value -> value.plus(duration));
    }
}
