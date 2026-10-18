package cn.iocoder.yudao.module.campus.identity.controller;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampusPlatformPrincipalGuardTest {

    private final CampusPlatformPrincipalGuard guard = new CampusPlatformPrincipalGuard(1L);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingPrincipalRequiresAuthentication() {
        assertThatThrownBy(guard::requiredMemberId)
                .isInstanceOfSatisfying(CampusApiException.class,
                        exception -> assertThat(exception.getSpec()).isEqualTo(CampusErrorCodes.AUTH_REQUIRED));
    }

    @Test
    void tokenAudienceAndPlatformTenantAreBothServerEnforced() {
        authenticate(11L, UserTypeEnum.ADMIN, 1L);
        assertThatThrownBy(guard::requiredMemberId)
                .isInstanceOfSatisfying(CampusApiException.class,
                        exception -> assertThat(exception.getSpec()).isEqualTo(CampusErrorCodes.PERMISSION_DENIED));

        authenticate(12L, UserTypeEnum.MEMBER, 2L);
        assertThatThrownBy(guard::requiredMemberId)
                .isInstanceOfSatisfying(CampusApiException.class,
                        exception -> assertThat(exception.getSpec()).isEqualTo(CampusErrorCodes.PERMISSION_DENIED));

        authenticate(13L, UserTypeEnum.MEMBER, 1L);
        assertThat(guard.requiredMemberId()).isEqualTo(13L);
    }

    @Test
    void platformOperatorRequiresAdminAudience() {
        authenticate(21L, UserTypeEnum.ADMIN, 1L);
        assertThat(guard.requiredOperatorId()).isEqualTo(21L);
    }

    private static void authenticate(long userId, UserTypeEnum userType, long tenantId) {
        LoginUser user = new LoginUser();
        user.setId(userId);
        user.setUserType(userType.getValue());
        user.setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    }
}
