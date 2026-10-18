package cn.iocoder.yudao.module.campus.identity.controller.app;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.common.api.CampusResponse;
import cn.iocoder.yudao.module.campus.identity.api.IdentityApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Campus-path wrapper for the upstream member logout/revocation protocol. */
@Tag(name = "校园 - 微信登录")
@RestController
@TenantIgnore
@RequestMapping("/campus/v1/auth")
@RequiredArgsConstructor
public class CampusLogoutController {

    private final IdentityApi identityApi;
    private final SecurityProperties securityProperties;

    @PostMapping("/logout")
    @PermitAll
    @Operation(summary = "退出校园学生端会话")
    public CampusResponse<Boolean> logout(HttpServletRequest request) {
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        if (StrUtil.isNotBlank(token)) {
            identityApi.logout(token);
        }
        return CampusResponse.success(true);
    }
}
