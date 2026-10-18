package cn.iocoder.yudao.module.campus.common.outbox;

import java.time.Clock;
import java.util.Objects;

/** Operator replay keeps the original event id: it is never a new business fact. */
public final class OutboxReplayService {
    private final OutboxRepository repository; private final Clock clock;
    public OutboxReplayService(OutboxRepository repository){this(repository,Clock.systemUTC());}
    public OutboxReplayService(OutboxRepository repository,Clock clock){this.repository=Objects.requireNonNull(repository);this.clock=Objects.requireNonNull(clock);}
    /** @return false when the requested consumer already succeeded, so it is intentionally skipped. */
    public boolean replay(String eventId,String consumer,String reason,long expectedVersion){
        Objects.requireNonNull(eventId,"eventId");Objects.requireNonNull(consumer,"consumer");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("replay reason must not be blank");
        OutboxRecord event=repository.find(eventId).orElseThrow(()->new IllegalArgumentException("outbox event not found"));
        if(event.rowVersion()!=expectedVersion) throw new OutboxVersionConflictException(eventId);
        if (event.status() == OutboxStatus.PROCESSING && event.leaseUntil() != null
                && event.leaseUntil().isAfter(clock.instant())) {
            throw new IllegalStateException("cannot replay an active processing event");
        }
        // A target replay of an already-successful consumer would only create meaningless scheduler churn.
        // The dispatcher also keeps this guard, which covers races after this read.
        if (repository.consumptionStatus(eventId, consumer).orElse(null) == ConsumerStatus.SUCCEEDED) return false;
        return repository.replay(eventId,consumer,reason,expectedVersion,clock.instant());
    }
}
