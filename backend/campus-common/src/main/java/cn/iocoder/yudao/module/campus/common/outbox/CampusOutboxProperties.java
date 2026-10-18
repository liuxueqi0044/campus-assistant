package cn.iocoder.yudao.module.campus.common.outbox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Runtime tuning for M00.05. Correctness remains in the database lease protocol. */
@Data
@ConfigurationProperties(prefix = "campus.outbox")
public class CampusOutboxProperties {

    private boolean workerEnabled = true;
    private String workerId = "campus-local";
    private int batchSize = 50;
    private int maxAttempts = 8;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration initialBackoff = Duration.ofSeconds(1);
    private Duration maxBackoff = Duration.ofMinutes(5);
    private long pollIntervalMs = 1000;
}
