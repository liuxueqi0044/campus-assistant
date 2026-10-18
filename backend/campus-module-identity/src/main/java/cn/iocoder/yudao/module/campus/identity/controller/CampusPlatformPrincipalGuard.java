package cn.iocoder.yudao.module.campus.identity.controller;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Enforces the server-owned platform tenant and token audience at campus HTTP boundaries. */
@Component
public class CampusPlatformPrincipalGuard {

    private final long platformTenantId;

    public CampusPlatformPrincipalGuard(@Value("${campus.platform-tenant-id:1}") long platformTenantId) {
        if (platformTenantId <= 0) {
            throw new IllegalArgumentException("campus.platform-tenant-id must be positive");
        }
        this.platformTenantId = platformTenantId;
    }

    public long requiredMemberId() {
        return required(UserTypeEnum.MEMBER);
    }

    public long requiredOperatorId() {
        return required(UserTypeEnum.ADMIN);
    }

    private long required(UserTypeEnum expectedType) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getId() == null || loginUser.getId() <= 0) {
            throw new CampusApiException(CampusErrorCodes.AUTH_REQUIRED);
        }
        if (!Objects.equals(loginUser.getUserType(), expectedType.getValue())
                || !Objects.equals(loginUser.getTenantId(), platformTenantId)) {
            throw new CampusApiException(CampusErrorCodes.PERMISSION_DENIED);
        }
        return loginUser.getId();
    }
}
