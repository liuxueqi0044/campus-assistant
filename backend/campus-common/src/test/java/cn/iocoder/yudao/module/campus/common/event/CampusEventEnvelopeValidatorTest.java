package cn.iocoder.yudao.module.campus.common.event;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M00.02 验收（A 级）：事件信封字段完整、载荷不含敏感值、未知版本可观察失败。
 */
class CampusEventEnvelopeValidatorTest {

    private static final String TYPE = "campus.order.created";

    @Test
    void validate_shouldAcceptCompleteEnvelope() {
        CampusEventEnvelope envelope = envelope(Map.of("orderId", "9007199254740993", "amountFen", 1234));

        assertThat(CampusEventEnvelopeValidator.validate(envelope)).isSameAs(envelope);
    }

    @Test
    void validate_shouldReportAllMissingFieldsWithFrozenCode() {
        CampusEventEnvelope envelope = envelope(Map.of());
        envelope.setEventId("  ");
        envelope.setType(null);
        envelope.setOccurredAt(null);

        assertThatThrownBy(() -> CampusEventEnvelopeValidator.validate(envelope))
                .isInstanceOf(CampusApiException.class)
                .hasMessageContaining("eventId")
                .hasMessageContaining("type")
                .hasMessageContaining("occurredAt")
                .extracting(ex -> ((CampusApiException) ex).getCode())
                .isEqualTo(CampusErrorCodes.EVENT_ENVELOPE_INVALID.getCode());
    }

    @Test
    void validate_shouldRejectSensitivePayloadEvenWhenNested() {
        CampusEventEnvelope envelope = envelope(Map.of("receiver", Map.of("pickup_code", "A-1234"),
                "history", List.of(Map.of("idCard", "330000200001010000"))));

        CampusApiException error = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> CampusEventEnvelopeValidator.validate(envelope), CampusApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(CampusErrorCodes.EVENT_PAYLOAD_FORBIDDEN.getCode());
        // 原因里必须点到具体键名，运维才不需要翻日志猜是哪一层嵌套
        assertThat(error.getMessage()).containsAnyOf("pickup_code", "idCard");
    }

    @Test
    void validate_shouldUseSameNormalizedSensitiveKeysAsSchema() {
        for (String key : List.of("id_card_no", "ID-CARD-NO", "id-card-number", "i_d_c_a_r_d",
                "pickup_code", "CERT-NO")) {
            CampusEventEnvelope envelope = envelope(Map.of(key, "secret"));
            assertThatThrownBy(() -> CampusEventEnvelopeValidator.validate(envelope))
                    .as("敏感键必须拒绝: %s", key)
                    .isInstanceOf(CampusApiException.class)
                    .extracting(ex -> ((CampusApiException) ex).getCode())
                    .isEqualTo(CampusErrorCodes.EVENT_PAYLOAD_FORBIDDEN.getCode());
        }
    }

    @Test
    void envelope_shouldSerializeOccurredAtAsRfc3339String() throws Exception {
        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(envelope(Map.of())));

        assertThat(json.get("occurredAt").isTextual()).isTrue();
        assertThat(json.get("occurredAt").textValue()).isEqualTo("2026-09-17T10:15:30+08:00");
        assertThat(json.get("aggregateId").isTextual()).isTrue();
        assertThat(json.get("schoolId").isTextual()).isTrue();
    }

    @Test
    void versionPolicy_shouldMakeUnknownTypeAndVersionObservable() {
        CampusEventVersionPolicy policy = CampusEventVersionPolicy.of(Map.of(TYPE, List.of(1, 2)));

        assertThat(policy.check(TYPE, 2)).isEqualTo(CampusEventVersionStatus.SUPPORTED);
        assertThat(policy.check(TYPE, 3)).isEqualTo(CampusEventVersionStatus.UNSUPPORTED_VERSION);
        assertThat(policy.check("campus.unknown.fact", 1)).isEqualTo(CampusEventVersionStatus.UNKNOWN_TYPE);

        assertThatThrownBy(() -> policy.requireSupported(TYPE, 3))
                .isInstanceOf(CampusApiException.class)
                .extracting(ex -> ((CampusApiException) ex).getCode())
                .isEqualTo(CampusErrorCodes.EVENT_VERSION_UNSUPPORTED.getCode());
    }

    @Test
    void versionPolicy_shouldBeImmutableAfterConstruction() {
        java.util.List<Integer> versions = new java.util.ArrayList<>(List.of(1));
        CampusEventVersionPolicy policy = CampusEventVersionPolicy.of(Map.of(TYPE, versions));

        versions.add(3);

        assertThat(policy.check(TYPE, 3)).isEqualTo(CampusEventVersionStatus.UNSUPPORTED_VERSION);
    }

    private static CampusEventEnvelope envelope(Map<String, Object> payload) {
        return CampusEventEnvelope.builder()
                .eventId("evt-1")
                .type(TYPE)
                .version(1)
                .aggregateId("9007199254740993")
                .aggregateVersion(3L)
                .schoolId("S1")
                .occurredAt(OffsetDateTime.of(2026, 9, 17, 10, 15, 30, 0, ZoneOffset.ofHours(8)))
                .payload(payload)
                .build();
    }

}
