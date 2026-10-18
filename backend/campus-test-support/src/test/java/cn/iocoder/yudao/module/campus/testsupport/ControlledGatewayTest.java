package cn.iocoder.yudao.module.campus.testsupport;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControlledGatewayTest {

    @Test
    void recordsCallsAndConsumesSuccessThenFailureInOrder() {
        MutableClock clock = MutableClock.at(Instant.parse("2026-09-17T00:00:00Z"));
        ControlledGateway<String, String> gateway = new ControlledGateway<>(clock);
        gateway.succeedNext("ok");
        gateway.failNext(new IllegalStateException("timeout"));

        assertThat(gateway.invoke("first")).isEqualTo("ok");
        clock.advance(java.time.Duration.ofSeconds(1));
        assertThatThrownBy(() -> gateway.invoke("second"))
                .isInstanceOf(IllegalStateException.class).hasMessage("timeout");
        assertThat(gateway.calls()).extracting(ControlledGateway.GatewayCall::request)
                .containsExactly("first", "second");
        assertThat(gateway.calls()).extracting(ControlledGateway.GatewayCall::invokedAt)
                .containsExactly(Instant.parse("2026-09-17T00:00:00Z"), Instant.parse("2026-09-17T00:00:01Z"));
    }

    @Test
    void unconfiguredGatewayFailsClearly() {
        ControlledGateway<String, String> gateway = new ControlledGateway<>(MutableClock.at(Instant.EPOCH));

        assertThatThrownBy(() -> gateway.invoke("request"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No controlled gateway outcome configured");
        assertThat(gateway.callCount()).isOne();
    }
}
