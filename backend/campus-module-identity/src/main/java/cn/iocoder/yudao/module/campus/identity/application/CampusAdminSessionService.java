package cn.iocoder.yudao.module.campus.identity.application;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.infrastructure.PlatformTenantExecutor;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2RefreshTokenMapper;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants.CLIENT_ID_DEFAULT;

/**
 * Campus-only wrapper around upstream admin authentication. Anonymous headers never select the tenant:
 * every account and token operation runs in the server-owned platform tenant.
 */
@Service
public class CampusAdminSessionService {

    private final AdminAuthService authService;
    private final OAuth2TokenService tokenService;
    private final OAuth2AccessTokenMapper accessTokens;
    private final OAuth2RefreshTokenMapper refreshTokens;
    private final PlatformTenantExecutor tenantExecutor;
    private final JdbcTemplate jdbc;
    private final long platformTenantId;

    public CampusAdminSessionService(AdminAuthService authService,
                                     OAuth2TokenService tokenService,
                                     OAuth2AccessTokenMapper accessTokens,
                                     OAuth2RefreshTokenMapper refreshTokens,
                                     PlatformTenantExecutor tenantExecutor,
                                     JdbcTemplate jdbc,
                                     @Value("${campus.platform-tenant-id:1}") long platformTenantId) {
        if (platformTenantId <= 0) {
            throw new IllegalArgumentException("campus.platform-tenant-id must be positive");
        }
        this.authService = Objects.requireNonNull(authService);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.accessTokens = Objects.requireNonNull(accessTokens);
        this.refreshTokens = Objects.requireNonNull(refreshTokens);
        this.tenantExecutor = Objects.requireNonNull(tenantExecutor);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.platformTenantId = platformTenantId;
    }

    public CampusAdminSessionResult login(String username, String password, String captchaVerification) {
        AuthLoginReqVO upstream = new AuthLoginReqVO();
        upstream.setUsername(username);
        upstream.setPassword(password);
        upstream.setCaptchaVerification(captchaVerification);
        return tenantExecutor.execute(() -> verified(authService.login(upstream), null));
    }

    public CampusAdminSessionResult refresh(String refreshToken) {
        OAuth2RefreshTokenDO stored = refreshTokens.selectByRefreshToken(refreshToken);
        if (!validRefreshToken(stored)) {
            throw unauthorized();
        }
        return tenantExecutor.execute(() -> verified(authService.refreshToken(refreshToken), stored.getUserId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void logout(long operatorId, String accessToken) {
        tenantExecutor.execute(() -> {
            OAuth2AccessTokenDO stored = accessTokens.selectByAccessToken(accessToken);
            if (!validAccessToken(stored, operatorId, null)) {
                throw unauthorized();
            }
            authService.logout(accessToken, LoginLogTypeEnum.LOGOUT_SELF.getType());
            // Upstream OAuth uses logical deletion. Campus logout's contract requires the current
            // access/refresh rows to be absent, so physically remove only this validated pair.
            jdbc.update("DELETE FROM system_oauth2_access_token WHERE id = ? AND tenant_id = ?",
                    stored.getId(), platformTenantId);
            jdbc.update("DELETE FROM system_oauth2_refresh_token WHERE refresh_token = ? AND tenant_id = ?",
                    stored.getRefreshToken(), platformTenantId);
            return null;
        });
    }

    private CampusAdminSessionResult verified(AuthLoginRespVO response, Long expectedUserId) {
        if (response == null || response.getUserId() == null || response.getUserId() <= 0
                || StrUtil.isBlank(response.getAccessToken()) || StrUtil.isBlank(response.getRefreshToken())
                || response.getExpiresTime() == null) {
            throw unauthorized();
        }
        OAuth2AccessTokenDO stored = accessTokens.selectByAccessToken(response.getAccessToken());
        if (!validAccessToken(stored, response.getUserId(), response.getRefreshToken())
                || expectedUserId != null && !Objects.equals(expectedUserId, response.getUserId())) {
            tokenService.removeAccessToken(response.getAccessToken());
            throw unauthorized();
        }
        return new CampusAdminSessionResult(response.getUserId(), response.getAccessToken(),
                response.getRefreshToken(), response.getExpiresTime());
    }

    private boolean validAccessToken(OAuth2AccessTokenDO token, long expectedUserId, String expectedRefreshToken) {
        return token != null
                && Objects.equals(token.getTenantId(), platformTenantId)
                && Objects.equals(token.getUserType(), UserTypeEnum.ADMIN.getValue())
                && Objects.equals(token.getUserId(), expectedUserId)
                && Objects.equals(token.getClientId(), CLIENT_ID_DEFAULT)
                && (expectedRefreshToken == null || Objects.equals(token.getRefreshToken(), expectedRefreshToken));
    }

    private boolean validRefreshToken(OAuth2RefreshTokenDO token) {
        return token != null
                && Objects.equals(token.getTenantId(), platformTenantId)
                && Objects.equals(token.getUserType(), UserTypeEnum.ADMIN.getValue())
                && token.getUserId() != null && token.getUserId() > 0
                && Objects.equals(token.getClientId(), CLIENT_ID_DEFAULT);
    }

    private static CampusApiException unauthorized() {
        return new CampusApiException(CampusErrorCodes.AUTH_REQUIRED);
    }
}
