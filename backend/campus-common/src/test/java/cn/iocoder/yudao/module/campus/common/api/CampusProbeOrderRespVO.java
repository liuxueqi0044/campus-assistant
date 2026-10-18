package cn.iocoder.yudao.module.campus.common.api;

import cn.iocoder.yudao.module.campus.common.api.json.CampusIdString;
import cn.iocoder.yudao.module.campus.common.api.json.CampusTimestamp;
import cn.iocoder.yudao.module.campus.common.api.json.MoneyFen;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * M00.02 验收用响应 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampusProbeOrderRespVO {

    @CampusIdString
    private String id;

    private String schoolId;

    private MoneyFen amountFen;

    @CampusTimestamp
    private OffsetDateTime createdAt;

}
