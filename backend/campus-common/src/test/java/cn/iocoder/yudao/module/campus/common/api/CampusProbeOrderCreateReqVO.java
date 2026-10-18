package cn.iocoder.yudao.module.campus.common.api;

import cn.iocoder.yudao.module.campus.common.api.json.CampusIdString;
import cn.iocoder.yudao.module.campus.common.api.json.CampusTimestamp;
import cn.iocoder.yudao.module.campus.common.api.json.MoneyFen;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * M00.02 验收用请求 DTO：演示「ID 用 {@link CampusIdString} 固定字符串、金额用 {@link MoneyFen}」。
 *
 * <p>仅存在于测试源码，不会进入交付 jar；真实 DTO 由各业务模块按同一规则编写。
 */
@Data
public class CampusProbeOrderCreateReqVO {

    @CampusIdString
    @NotNull(message = "买家编号不能为空")
    private String buyerId;

    private String schoolId;

    @NotNull(message = "金额不能为空")
    private MoneyFen amountFen;

    /** 约定的期望时间；用于验收「时间必须带时区偏移」 */
    @CampusTimestamp
    private OffsetDateTime expectedAt;

}
