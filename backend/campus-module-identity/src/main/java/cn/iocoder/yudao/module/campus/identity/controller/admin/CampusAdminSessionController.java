package cn.iocoder.yudao.module.campus.identity.controller.admin;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.common.api.CampusResponse;
import cn.iocoder.yudao.module.campus.identity.application.CampusAdminSessionService;
import cn.iocoder.yudao.module.campus.identity.controller.CampusPlatformPrincipalGuard;
import cn.iocoder.yudao.module.campus.identity.controller.admin.vo.CampusAdminLoginRequest;
import cn.iocoder.yudao.module.campus.identity.controller.admin.vo.CampusAdminRefreshRequest;
import cn.iocoder.yudao.module.campus.identity.controller.admin.vo.CampusAdminSessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "校园管理端 - 会话")
@RestController
@TenantIgnore
@RequestMapping("/campus/v1/session")
@RequiredArgsConstructor
public class CampusAdminSessionController {

    private final CampusAdminSessionService sessionService;
    private final CampusPlatformPrincipalGuard principalGuard;
    private final SecurityProperties securityProperties;

    @PostMapping("/login")
    @PermitAll
    @Operation(summary = "登录固定平台租户的校园管理端")
    public CampusResponse<CampusAdminSessionResponse> login(
            @RequestBody @Valid CampusAdminLoginRequest request) {
        return CampusResponse.success(CampusAdminSessionResponse.from(sessionService.login(request.getUsername(),
                request.getPassword(), request.getCaptchaVerification())));
    }

    @PostMapping("/refresh")
    @PermitAll
    @Operation(summary = "刷新固定平台租户的校园管理端会话")
    public CampusResponse<CampusAdminSessionResponse> refresh(
            @RequestBody @Valid CampusAdminRefreshRequest request) {
        return CampusResponse.success(CampusAdminSessionResponse.from(
                sessionService.refresh(request.getRefreshToken())));
    }

    @PostMapping("/logout")
    @Operation(summary = "退出当前校园管理端会话")
    public CampusResponse<Boolean> logout(HttpServletRequest request) {
        long operatorId = principalGuard.requiredOperatorId();
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        if (StrUtil.isBlank(token)) {
            throw new CampusApiException(CampusErrorCodes.AUTH_REQUIRED);
        }
        sessionService.logout(operatorId, token);
        return CampusResponse.success(true);
    }
}
