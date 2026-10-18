package cn.iocoder.yudao.module.campus.testsupport;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** One-shot barrier that lets a test release all participating workers at the same controlled instant. */
public final class ConcurrencyBarrier {

    private final CountDownLatch ready;
    private final CountDownLatch start = new CountDownLatch(1);

    public ConcurrencyBarrier(int participants) {
        if (participants < 1) {
            throw new IllegalArgumentException("participants must be positive");
        }
        this.ready = new CountDownLatch(participants);
    }

    public void awaitStart(Duration timeout) throws InterruptedException {
        ready.countDown();
        if (!start.await(requirePositive(timeout).toNanos(), TimeUnit.NANOSECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent start");
        }
    }

    public void awaitReady(Duration timeout) throws InterruptedException {
        if (!ready.await(requirePositive(timeout).toNanos(), TimeUnit.NANOSECONDS)) {
            throw new IllegalStateException("Timed out waiting for all participants to become ready");
        }
    }

    public void release() {
        start.countDown();
    }

    public long waitingParticipants() {
        return ready.getCount();
    }

    private static Duration requirePositive(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return duration;
    }
}
