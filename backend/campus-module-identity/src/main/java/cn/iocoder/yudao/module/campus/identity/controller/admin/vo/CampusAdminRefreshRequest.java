package cn.iocoder.yudao.module.campus.identity.controller.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CampusAdminRefreshRequest {

    @NotBlank(message = "refreshToken 不能为空")
    @Size(max = 256, message = "refreshToken 长度不能超过 256")
    private String refreshToken;
}
