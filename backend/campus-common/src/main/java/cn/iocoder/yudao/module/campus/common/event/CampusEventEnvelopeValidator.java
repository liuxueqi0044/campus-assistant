package cn.iocoder.yudao.module.campus.common.event;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorDetail;
import cn.iocoder.yudao.module.campus.common.api.CampusRetryAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * M00.02 事件信封校验：字段完整性 + 载荷敏感字段扫描。
 *
 * <p>校验失败必须让事件进入可观察失败（错误码固定、原因可读），而不是带着缺失字段继续落库。
 * 载荷只允许业务字段：证件号与取件码属于敏感值，必须由持有人按需查询，不能进入事件表。
 */
public final class CampusEventEnvelopeValidator {

    /** 归一化后的禁止键名（去掉下划线、连字符并转小写后比较） */
    public static final Set<String> FORBIDDEN_PAYLOAD_KEYS = Set.of(
            "idcard", "idcardno", "idcardnumber", "idno", "certno", "idnumber", "pickupcode");

    private CampusEventEnvelopeValidator() {
    }

    /**
     * 校验事件信封；通过则返回原对象，便于链式调用。
     */
    public static CampusEventEnvelope validate(CampusEventEnvelope envelope) {
        if (envelope == null) {
            throw invalidEnvelope("envelope");
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(envelope.getEventId())) {
            missing.add("eventId");
        }
        if (isBlank(envelope.getType())) {
            missing.add("type");
        }
        if (isBlank(envelope.getAggregateId())) {
            missing.add("aggregateId");
        }
        if (isBlank(envelope.getSchoolId())) {
            missing.add("schoolId");
        }
        if (envelope.getOccurredAt() == null) {
            missing.add("occurredAt");
        }
        if (envelope.getVersion() < 1) {
            missing.add("version");
        }
        if (envelope.getAggregateVersion() < 0) {
            missing.add("aggregateVersion");
        }
        if (!missing.isEmpty()) {
            throw invalidEnvelope(String.join(",", missing));
        }
        if (envelope.getPayload() == null) {
            throw invalidEnvelope("payload");
        }
        String forbidden = findForbiddenKey(envelope.getPayload());
        if (forbidden != null) {
            throw new CampusApiException(CampusErrorCodes.EVENT_PAYLOAD_FORBIDDEN,
                    CampusErrorDetail.of(CampusRetryAction.OPERATOR), forbidden);
        }
        return envelope;
    }

    private static CampusApiException invalidEnvelope(String fields) {
        return new CampusApiException(CampusErrorCodes.EVENT_ENVELOPE_INVALID,
                CampusErrorDetail.of(CampusRetryAction.OPERATOR), fields);
    }

    /** 递归查找禁止键；返回原始键名便于定位，未命中返回 null */
    private static String findForbiddenKey(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (FORBIDDEN_PAYLOAD_KEYS.contains(normalize(key))) {
                    return key;
                }
                String nested = findForbiddenKey(entry.getValue());
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                String nested = findForbiddenKey(element);
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (value instanceof Object[] array) {
            for (Object element : array) {
                String nested = findForbiddenKey(element);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static String normalize(String key) {
        return key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
