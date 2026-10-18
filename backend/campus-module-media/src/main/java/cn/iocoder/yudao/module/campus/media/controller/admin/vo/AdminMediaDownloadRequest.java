package cn.iocoder.yudao.module.campus.media.controller.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminMediaDownloadRequest {

    @NotBlank(message = "caseReference 不能为空")
    @Size(max = 128, message = "caseReference 长度不能超过 128")
    private String caseReference;

    @NotBlank(message = "reason 不能为空")
    @Size(max = 500, message = "reason 长度不能超过 500")
    private String reason;
}
