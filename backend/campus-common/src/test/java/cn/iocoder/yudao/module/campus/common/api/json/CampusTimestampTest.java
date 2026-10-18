package cn.iocoder.yudao.module.campus.common.api.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M00.02 验收（A 级）：时间在 JSON 中固定为带偏移的 RFC 3339 字符串。
 *
 * <p>回归背景：上游 {@code application.yaml} 打开了
 * {@code spring.jackson.serialization.write-dates-as-timestamps}，若不显式控制，
 * 时间会以 epoch 数字输出。
 */
class CampusTimestampTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void timestamp_shouldSerializeAsOffsetString() throws Exception {
        TimeHolder holder = new TimeHolder(OffsetDateTime.parse("2026-09-17T10:15:30+08:00"));

        assertThat(objectMapper.writeValueAsString(holder))
                .isEqualTo("{\"occurredAt\":\"2026-09-17T10:15:30+08:00\"}");
    }

    @Test
    void timestamp_shouldAcceptUtcAndOffsetForms() throws Exception {
        TimeHolder utc = objectMapper.readValue("{\"occurredAt\":\"2026-09-17T02:15:30Z\"}", TimeHolder.class);
        TimeHolder offset = objectMapper.readValue("{\"occurredAt\":\"2026-09-17T10:15:30+08:00\"}",
                TimeHolder.class);

        assertThat(utc.getOccurredAt()).isEqualTo(offset.getOccurredAt());
    }

    @Test
    void timestamp_shouldRejectEpochNumberAndLocalTime() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"occurredAt\":1789611330}", TimeHolder.class))
                .hasRootCauseInstanceOf(CampusTimestampFormatException.class);
        assertThatThrownBy(() -> objectMapper.readValue("{\"occurredAt\":\"2026-09-17T10:15:30\"}",
                        TimeHolder.class))
                .hasRootCauseInstanceOf(CampusTimestampFormatException.class);
    }

    /** 用例内部 DTO */
    static class TimeHolder {

        @CampusTimestamp
        private OffsetDateTime occurredAt;

        TimeHolder() {
        }

        TimeHolder(OffsetDateTime occurredAt) {
            this.occurredAt = occurredAt;
        }

        public OffsetDateTime getOccurredAt() {
            return occurredAt;
        }

        public void setOccurredAt(OffsetDateTime occurredAt) {
            this.occurredAt = occurredAt;
        }

    }

}
