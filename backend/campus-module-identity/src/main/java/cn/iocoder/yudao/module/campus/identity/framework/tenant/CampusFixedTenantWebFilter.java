package cn.iocoder.yudao.module.campus.identity.framework.tenant;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Pins campus HTTP requests to the configured platform tenant before tenant security runs. */
public final class CampusFixedTenantWebFilter extends OncePerRequestFilter {

    static final int ORDER = -101;

    private final long platformTenantId;

    public CampusFixedTenantWebFilter(long platformTenantId) {
        if (platformTenantId <= 0) {
            throw new IllegalArgumentException("campus.platform-tenant-id must be positive");
        }
        this.platformTenantId = platformTenantId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!matchesFixedTenantPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        Long previousTenantId = TenantContextHolder.getTenantId();
        TenantContextHolder.setTenantId(platformTenantId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Restore only the tenant slot; the surrounding tenant context filter owns final cleanup.
            TenantContextHolder.setTenantId(previousTenantId);
        }
    }

    static boolean matchesFixedTenantPath(HttpServletRequest request) {
        if (request == null || request.getRequestURI() == null) {
            return false;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String method = request.getMethod();
        return "GET".equalsIgnoreCase(method) && (path.equals("/admin-api/campus/v1/context")
                || atOrBelow(path, "/admin-api/campus/v1/verifications"))
                || "POST".equalsIgnoreCase(method) && atOrBelow(path, "/admin-api/campus/v1/session");
    }

    private static boolean atOrBelow(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
