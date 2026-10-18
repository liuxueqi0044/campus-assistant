package cn.iocoder.yudao.module.campus.common.event;

import cn.iocoder.yudao.module.campus.common.api.json.CampusTimestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * M00.02 事件信封（冻结字段）。
 *
 * <p>每个跨域事实都必须带事件编号、类型与版本、聚合编号与版本、学校范围、发生时间；
 * 载荷只能放业务字段，禁止证件与取件码等敏感值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusEventEnvelope implements Serializable {

    /** 事件编号：同一事实只生成一次，重放沿用原值 */
    private String eventId;

    /** 事件类型，例如 {@code campus.order.created} */
    private String type;

    /** 事件结构版本，从 1 开始 */
    private int version;

    /** 聚合编号（字符串形式） */
    private String aggregateId;

    /** 聚合版本，用于乱序判定 */
    private long aggregateVersion;

    /** 学校范围编号 */
    private String schoolId;

    /** 事实发生时间：RFC 3339 且带偏移 */
    @CampusTimestamp
    private OffsetDateTime occurredAt;

    /** 产生该事件请求的追踪号，便于跨服务排查 */
    private String traceId;

    /** 业务载荷 */
    private Map<String, Object> payload;

}
