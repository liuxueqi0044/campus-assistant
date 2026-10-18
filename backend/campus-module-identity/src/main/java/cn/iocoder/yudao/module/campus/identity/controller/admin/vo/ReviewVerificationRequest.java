package cn.iocoder.yudao.module.campus.identity.controller.admin.vo;

import cn.iocoder.yudao.module.campus.identity.application.verification.ReviewDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewVerificationRequest {

    @NotNull(message = "decision 不能为空")
    private ReviewDecision decision;

    @NotBlank(message = "reason 不能为空")
    @Size(max = 500, message = "reason 长度不能超过 500")
    private String reason;

    @NotNull(message = "expectedVersion 不能为空")
    @Positive(message = "expectedVersion 必须为正整数")
    private Long expectedVersion;
}
