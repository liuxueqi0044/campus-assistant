package cn.iocoder.yudao.module.campus.common.baseline;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * M00.01 真实 Valkey 兼容验收。默认跳过；只有显式传入 {@code -Dcampus.valkey.it=true}
 * 才连接本地隔离实例，避免普通单元测试误连共享或生产缓存。
 */
class ValkeyCompatibilityIT {

    @Test
    void redisson_shouldSupportValueExpiryLuaAndDistributedLock() throws InterruptedException {
        assumeTrue(Boolean.getBoolean("campus.valkey.it"),
                "set -Dcampus.valkey.it=true to run the isolated Valkey integration test");

        String host = System.getProperty("campus.valkey.host", "127.0.0.1");
        int port = Integer.getInteger("campus.valkey.port", 6379);
        String password = System.getProperty("campus.valkey.password", "");
        String prefix = "campus:m00:valkey-it:" + UUID.randomUUID();
        String valueKey = prefix + ":value";
        String counterKey = prefix + ":counter";
        String lockKey = prefix + ":lock";

        Config config = new Config();
        var server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(0)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(2);
        if (!password.isBlank()) {
            server.setPassword(password);
        }

        RedissonClient client = Redisson.create(config);
        try {
            RBucket<String> bucket = client.getBucket(valueKey);
            bucket.set("ok", Duration.ofSeconds(30));
            assertThat(bucket.get()).isEqualTo("ok");
            assertThat(bucket.remainTimeToLive()).isPositive();

            Number counter = client.getScript().eval(
                    RScript.Mode.READ_WRITE,
                    "return redis.call('INCR', KEYS[1])",
                    RScript.ReturnType.LONG,
                    List.of(counterKey));
            assertThat(counter.longValue()).isEqualTo(1L);

            RLock lock = client.getLock(lockKey);
            assertThat(lock.tryLock(5, 10, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
            } finally {
                lock.unlock();
            }
            assertThat(lock.isLocked()).isFalse();
        } finally {
            client.getKeys().delete(valueKey, counterKey, lockKey);
            client.shutdown();
        }
    }

}
