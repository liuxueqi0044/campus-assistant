package cn.iocoder.yudao.server.campus.outbox;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.common.outbox.OutboxClaimJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Default monolith poller. Disable it when the Quartz job is configured as the sole trigger. */
@Component
@ConditionalOnProperty(prefix = "campus.outbox", name = "worker-enabled",
        havingValue = "true", matchIfMissing = true)
public class CampusOutboxScheduledPoller {

    private final OutboxClaimJob claimJob;

    public CampusOutboxScheduledPoller(OutboxClaimJob claimJob) {
        this.claimJob = claimJob;
    }

    @Scheduled(fixedDelayString = "${campus.outbox.poll-interval-ms:1000}")
    @TenantIgnore
    public void poll() {
        claimJob.runOnce();
    }
}
