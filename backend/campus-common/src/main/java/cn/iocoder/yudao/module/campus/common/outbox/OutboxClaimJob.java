package cn.iocoder.yudao.module.campus.common.outbox;

import java.util.Objects;

/** Plain callable entry point; scheduler-specific JobHandler wiring deliberately belongs to the application module. */
public final class OutboxClaimJob {
    private final OutboxDispatcher dispatcher;
    public OutboxClaimJob(OutboxDispatcher dispatcher){this.dispatcher=Objects.requireNonNull(dispatcher);}
    public int runOnce(){return dispatcher.dispatchBatch();}
}
