package cn.iocoder.yudao.module.campus.identity.controller.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CampusAdminLoginRequest {

    @NotBlank(message = "username 不能为空")
    @Size(min = 4, max = 30, message = "username 长度必须为 4-30")
    @Pattern(regexp = "^[a-zA-Z0-9]{4,30}$", message = "username 只能包含数字和字母")
    private String username;

    @NotBlank(message = "password 不能为空")
    @Size(min = 4, max = 16, message = "password 长度必须为 4-16")
    private String password;

    @Size(max = 2048, message = "captchaVerification 长度不能超过 2048")
    private String captchaVerification;
}
