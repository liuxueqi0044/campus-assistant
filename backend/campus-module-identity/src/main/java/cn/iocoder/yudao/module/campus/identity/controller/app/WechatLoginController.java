package cn.iocoder.yudao.module.campus.identity.controller.app;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.common.api.CampusResponse;
import cn.iocoder.yudao.module.campus.identity.api.IdentityApi;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.WechatLoginRequest;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.WechatLoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** M01.01 student-only WeChat sign-in endpoint. */
@Tag(name = "校园 - 微信登录")
@RestController
@TenantIgnore
@RequestMapping("/campus/v1/auth/wechat")
@RequiredArgsConstructor
public class WechatLoginController {

    private final IdentityApi identityApi;

    @PostMapping("/login")
    @PermitAll
    @Operation(summary = "使用微信小程序 code 登录")
    public CampusResponse<WechatLoginResponse> login(@RequestBody @Valid WechatLoginRequest request) {
        return CampusResponse.success(WechatLoginResponse.from(identityApi.loginWithWechatCode(request.getCode())));
    }
}
