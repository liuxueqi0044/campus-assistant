package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxReplayServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void successfulConsumerIsSkipped() {
        OutboxRepository repository = mock(OutboxRepository.class);
        when(repository.find("e-1")).thenReturn(Optional.of(row(1, OutboxStatus.DEAD_LETTER, null)));
        when(repository.consumptionStatus("e-1", "one")).thenReturn(Optional.of(ConsumerStatus.SUCCEEDED));
        assertThat(new OutboxReplayService(repository, CLOCK).replay("e-1", "one", "manual replay", 1)).isFalse();
        verify(repository, never()).replay(any(), any(), any(), anyLong(), any());
    }

    @Test
    void reasonMustBeNonBlank() {
        OutboxRepository repository = mock(OutboxRepository.class);
        OutboxReplayService service = new OutboxReplayService(repository, CLOCK);
        assertThatThrownBy(() -> service.replay("e-1", "one", "  ", 1))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).find(any());
    }

    @Test
    void expectedVersionConflictIsRejected() {
        OutboxRepository repository = mock(OutboxRepository.class);
        when(repository.find("e-1")).thenReturn(Optional.of(row(2, OutboxStatus.DEAD_LETTER, null)));
        assertThatThrownBy(() -> new OutboxReplayService(repository, CLOCK)
                .replay("e-1", "one", "manual replay", 1))
                .isInstanceOf(OutboxVersionConflictException.class);
        verify(repository, never()).replay(any(), any(), any(), anyLong(), any());
    }

    @Test
    void activeProcessingCannotBeSilentlyReplayed() {
        OutboxRepository repository = mock(OutboxRepository.class);
        when(repository.find("e-1")).thenReturn(Optional.of(row(1, OutboxStatus.PROCESSING, NOW.plusSeconds(30))));
        assertThatThrownBy(() -> new OutboxReplayService(repository, CLOCK)
                .replay("e-1", "one", "manual replay", 1))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).replay(any(), any(), any(), anyLong(), any());
    }

    private static OutboxRecord row(long version, OutboxStatus status, Instant leaseUntil) {
        CampusEventEnvelope event = CampusEventEnvelope.builder().eventId("e-1").type("campus.test.created")
                .version(1).aggregateId("1").aggregateVersion(1).schoolId("1")
                .occurredAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)).payload(Map.of("safe", "yes")).build();
        return new OutboxRecord(event, status, NOW, 1, 3, "worker", leaseUntil, 1,
                null, null, null, null, version, null, null, null, null, NOW, NOW);
    }
}
