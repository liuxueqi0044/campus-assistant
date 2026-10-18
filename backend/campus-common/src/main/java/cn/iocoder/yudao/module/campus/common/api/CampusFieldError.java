package cn.iocoder.yudao.module.campus.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * M00.02 参数错误明细：只回显字段名与原因，不回显请求体原文与其他用户数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampusFieldError implements Serializable {

    /** 字段路径，例如 {@code amountFen} 或 {@code items[0].skuId} */
    private String field;

    /** 用户可阅读的原因 */
    private String reason;

}
