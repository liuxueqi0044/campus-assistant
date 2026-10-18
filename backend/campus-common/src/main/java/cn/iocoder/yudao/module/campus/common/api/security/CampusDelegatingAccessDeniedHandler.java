package cn.iocoder.yudao.module.campus.common.api.security;

import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.campus.common.api.CampusApiContract;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorDetail;
import cn.iocoder.yudao.module.campus.common.api.CampusPathMatcher;
import cn.iocoder.yudao.module.campus.common.api.CampusResponse;
import cn.iocoder.yudao.module.campus.common.api.CampusTraceContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** 校园路径使用冻结的 403 契约，其他路径原样委托芋道处理器。 */
public final class CampusDelegatingAccessDeniedHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler delegate;

    public CampusDelegatingAccessDeniedHandler(AccessDeniedHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException, ServletException {
        if (!CampusPathMatcher.isCampusPath(request)) {
            delegate.handle(request, response, exception);
            return;
        }
        String traceId = CampusTraceContext.traceIdOf(request);
        CampusResponse<CampusErrorDetail> body = CampusResponse.error(CampusErrorCodes.PERMISSION_DENIED,
                CampusErrorDetail.of(CampusErrorCodes.PERMISSION_DENIED.getRetryAction()));
        body.setTraceId(traceId);
        response.setStatus(CampusErrorCodes.PERMISSION_DENIED.getHttpStatusValue());
        response.setHeader(CampusApiContract.HEADER_TRACE_ID, traceId);
        ServletUtils.writeJSON(response, body);
    }

}
