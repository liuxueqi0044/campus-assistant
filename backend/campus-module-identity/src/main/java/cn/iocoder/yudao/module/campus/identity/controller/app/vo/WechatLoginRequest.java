package cn.iocoder.yudao.module.campus.identity.controller.app.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/** clientContext is diagnostic/UI context only; it cannot carry appId, openid or an authority scope. */
@Data
public class WechatLoginRequest {

    @NotBlank(message = "code 不能为空")
    @Size(max = 1024, message = "code 长度不能超过 1024")
    private String code;

    @Size(max = 16, message = "clientContext 字段数量不能超过 16")
    private Map<@Size(max = 64) String, @Size(max = 256) String> clientContext;
}
