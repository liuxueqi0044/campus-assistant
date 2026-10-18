package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Runs campus account/token operations in the server-owned platform tenant, never in a tenant supplied
 * by an anonymous request header. This intentionally does not use TenantUtils' Callable overload: that
 * helper wraps RuntimeException and would erase CampusApiException semantics.
 */
@Component
public class PlatformTenantExecutor {

    private final Long platformTenantId;

    public PlatformTenantExecutor(@Value("${campus.platform-tenant-id:1}") Long platformTenantId) {
        if (platformTenantId == null || platformTenantId <= 0) {
            throw new IllegalArgumentException("campus.platform-tenant-id 必须为正整数");
        }
        this.platformTenantId = platformTenantId;
    }

    public <T> T execute(Supplier<T> action) {
        Long oldTenantId = TenantContextHolder.getTenantId();
        boolean oldIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setTenantId(platformTenantId);
            TenantContextHolder.setIgnore(false);
            return action.get();
        } finally {
            TenantContextHolder.setTenantId(oldTenantId);
            TenantContextHolder.setIgnore(oldIgnore);
        }
    }
}
