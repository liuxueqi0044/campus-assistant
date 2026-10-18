package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.infrastructure.PlatformTenantExecutor;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2RefreshTokenMapper;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants.CLIENT_ID_DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampusAdminSessionServiceTest {

    private final AdminAuthService authService = mock(AdminAuthService.class);
    private final OAuth2TokenService tokenService = mock(OAuth2TokenService.class);
    private final OAuth2AccessTokenMapper accessTokens = mock(OAuth2AccessTokenMapper.class);
    private final OAuth2RefreshTokenMapper refreshTokens = mock(OAuth2RefreshTokenMapper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CampusAdminSessionService service = new CampusAdminSessionService(authService, tokenService,
            accessTokens, refreshTokens, new PlatformTenantExecutor(1L), jdbc, 1L);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void loginForcesPlatformTenantAndReturnsOnlyVerifiedAdminToken() {
        TenantContextHolder.setTenantId(99L);
        TenantContextHolder.setIgnore(true);
        AuthLoginRespVO response = response(7L, "access-7", "refresh-7");
        when(authService.login(any())).thenAnswer(invocation -> {
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(1L);
            assertThat(TenantContextHolder.isIgnore()).isFalse();
            assertThat(invocation.<cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginReqVO>getArgument(0)
                    .getSocialType()).isNull();
            return response;
        });
        when(accessTokens.selectByAccessToken("access-7")).thenReturn(access(7L, 1L, "access-7", "refresh-7"));

        CampusAdminSessionResult result = service.login("admin7", "safe-pass", "captcha");

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.accessToken()).isEqualTo("access-7");
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(99L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
    }

    @Test
    void refreshRejectsMemberOrOtherTenantBeforeCallingUpstream() {
        OAuth2RefreshTokenDO member = refresh(8L, 1L, UserTypeEnum.MEMBER.getValue(), "member-refresh");
        when(refreshTokens.selectByRefreshToken("member-refresh")).thenReturn(member);

        assertThatThrownBy(() -> service.refresh("member-refresh"))
                .isInstanceOfSatisfying(CampusApiException.class,
                        exception -> assertThat(exception.getSpec()).isEqualTo(CampusErrorCodes.AUTH_REQUIRED));
        verify(authService, never()).refreshToken(any());

        OAuth2RefreshTokenDO otherTenant = refresh(9L, 2L, UserTypeEnum.ADMIN.getValue(), "other-refresh");
        when(refreshTokens.selectByRefreshToken("other-refresh")).thenReturn(otherTenant);
        assertThatThrownBy(() -> service.refresh("other-refresh"))
                .isInstanceOf(CampusApiException.class);
        verify(authService, never()).refreshToken("other-refresh");
    }

    @Test
    void refreshRunsInPlatformTenantAndChecksReturnedAccessToken() {
        OAuth2RefreshTokenDO refresh = refresh(10L, 1L, UserTypeEnum.ADMIN.getValue(), "refresh-10");
        when(refreshTokens.selectByRefreshToken("refresh-10")).thenReturn(refresh);
        when(authService.refreshToken("refresh-10")).thenAnswer(invocation -> {
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(1L);
            return response(10L, "access-10", "refresh-10");
        });
        when(accessTokens.selectByAccessToken("access-10"))
                .thenReturn(access(10L, 1L, "access-10", "refresh-10"));

        CampusAdminSessionResult result = service.refresh("refresh-10");

        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.refreshToken()).isEqualTo("refresh-10");
    }

    @Test
    void logoutRequiresTheExactStoredAccessTokenForTheCurrentOperator() {
        TenantContextHolder.setTenantId(99L);
        OAuth2AccessTokenDO current = access(11L, 1L, "access-11", "refresh-11");
        current.setId(101L);
        when(accessTokens.selectByAccessToken("access-11"))
                .thenReturn(current);

        service.logout(11L, "access-11");

        verify(authService).logout("access-11", LoginLogTypeEnum.LOGOUT_SELF.getType());
        verify(jdbc).update("DELETE FROM system_oauth2_access_token WHERE id = ? AND tenant_id = ?", 101L, 1L);
        verify(jdbc).update("DELETE FROM system_oauth2_refresh_token WHERE refresh_token = ? AND tenant_id = ?",
                "refresh-11", 1L);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(99L);

        when(accessTokens.selectByAccessToken("refresh-11")).thenReturn(null);
        assertThatThrownBy(() -> service.logout(11L, "refresh-11"))
                .isInstanceOf(CampusApiException.class);
    }

    @Test
    void logoutRejectsUnknownAndOtherTenantTokensWithoutCallingUpstream() {
        when(accessTokens.selectByAccessToken("unknown")).thenReturn(null);
        assertThatThrownBy(() -> service.logout(11L, "unknown"))
                .isInstanceOfSatisfying(CampusApiException.class,
                        exception -> assertThat(exception.getSpec()).isEqualTo(CampusErrorCodes.AUTH_REQUIRED));

        when(accessTokens.selectByAccessToken("other-tenant"))
                .thenReturn(access(11L, 2L, "other-tenant", "other-refresh"));
        assertThatThrownBy(() -> service.logout(11L, "other-tenant"))
                .isInstanceOfSatisfying(CampusApiException.class,
                        exception -> assertThat(exception.getSpec()).isEqualTo(CampusErrorCodes.AUTH_REQUIRED));
        verify(authService, never()).logout(any(), any());
    }

    private static AuthLoginRespVO response(long userId, String accessToken, String refreshToken) {
        return AuthLoginRespVO.builder().userId(userId).accessToken(accessToken).refreshToken(refreshToken)
                .expiresTime(LocalDateTime.now().plusMinutes(30)).build();
    }

    private static OAuth2AccessTokenDO access(long userId, long tenantId, String accessToken, String refreshToken) {
        OAuth2AccessTokenDO token = new OAuth2AccessTokenDO().setAccessToken(accessToken).setRefreshToken(refreshToken)
                .setUserId(userId).setUserType(UserTypeEnum.ADMIN.getValue()).setClientId(CLIENT_ID_DEFAULT);
        token.setTenantId(tenantId);
        return token;
    }

    private static OAuth2RefreshTokenDO refresh(long userId, long tenantId, int userType, String refreshToken) {
        OAuth2RefreshTokenDO token = new OAuth2RefreshTokenDO().setRefreshToken(refreshToken).setUserId(userId)
                .setUserType(userType).setClientId(CLIENT_ID_DEFAULT);
        token.setTenantId(tenantId);
        return token;
    }
}
