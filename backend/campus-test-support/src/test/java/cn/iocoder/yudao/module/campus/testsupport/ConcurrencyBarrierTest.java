package cn.iocoder.yudao.module.campus.testsupport;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyBarrierTest {

    @Test
    void holdsWorkersUntilTheTestReleasesAUnifiedStart() throws Exception {
        ConcurrencyBarrier barrier = new ConcurrencyBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> {
                barrier.awaitStart(Duration.ofSeconds(2));
                return "first";
            });
            Future<String> second = executor.submit(() -> {
                barrier.awaitStart(Duration.ofSeconds(2));
                return "second";
            });

            barrier.awaitReady(Duration.ofSeconds(2));
            assertThat(first.isDone()).isFalse();
            assertThat(second.isDone()).isFalse();
            barrier.release();

            assertThat(first.get()).isEqualTo("first");
            assertThat(second.get()).isEqualTo("second");
        } finally {
            executor.shutdownNow();
        }
    }
}
