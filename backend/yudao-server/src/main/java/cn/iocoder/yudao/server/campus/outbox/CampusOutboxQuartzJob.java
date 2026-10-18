package cn.iocoder.yudao.server.campus.outbox;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.common.outbox.OutboxClaimJob;
import org.springframework.stereotype.Component;

/** Optional Quartz/admin trigger. Retry remains persisted in campus_outbox, not in Quartz. */
@Component("campusOutboxClaimJob")
public class CampusOutboxQuartzJob implements JobHandler {

    private final OutboxClaimJob claimJob;

    public CampusOutboxQuartzJob(OutboxClaimJob claimJob) {
        this.claimJob = claimJob;
    }

    @Override
    @TenantIgnore
    public String execute(String param) {
        int claimed = claimJob.runOnce();
        return "outbox claimed " + claimed + " event(s)";
    }
}
