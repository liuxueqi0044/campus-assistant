package cn.iocoder.yudao.module.campus.testsupport;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class MutableClockTest {

    @Test
    void advancesDeterministicallyAndSharesInstantAcrossZoneViews() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-17T00:00:00Z"), ZoneId.of("UTC"));

        assertThat(clock.advance(Duration.ofMinutes(5))).isEqualTo(Instant.parse("2026-09-17T00:05:00Z"));
        assertThat(clock.withZone(ZoneId.of("Asia/Shanghai")).instant()).isEqualTo(clock.instant());
        assertThat(clock.withZone(ZoneId.of("Asia/Shanghai")).getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
    }
}
