package cn.iocoder.yudao.module.campus.common.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 校园 HTTP 边界判定。
 *
 * <p>路径必须等于冻结前缀，或位于该前缀的下一层；不能用普通 {@code startsWith}
 * 把 {@code /app-api/campus/v10} 误判为 v1 接口。
 */
public final class CampusPathMatcher {

    private CampusPathMatcher() {
    }

    public static boolean isCampusPath(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return atOrBelow(path, CampusApiContract.APP_API_PREFIX)
                || atOrBelow(path, CampusApiContract.ADMIN_API_PREFIX)
                || atOrBelow(path, "/health");
    }

    private static boolean atOrBelow(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

}
