package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

/** JSON-tree comparison treats object key ordering as immaterial without storing a lossy hash. */
final class OutboxEnvelopeEquality {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private OutboxEnvelopeEquality() { }
    static boolean same(CampusEventEnvelope left, CampusEventEnvelope right) {
        return Objects.equals(left.getEventId(), right.getEventId()) && Objects.equals(left.getType(), right.getType())
                && left.getVersion() == right.getVersion() && Objects.equals(left.getAggregateId(), right.getAggregateId())
                && left.getAggregateVersion() == right.getAggregateVersion() && Objects.equals(left.getSchoolId(), right.getSchoolId())
                && Objects.equals(left.getOccurredAt().toInstant(), right.getOccurredAt().toInstant()) && Objects.equals(left.getTraceId(), right.getTraceId())
                && MAPPER.valueToTree(left.getPayload()).equals(MAPPER.valueToTree(right.getPayload()));
    }
}
