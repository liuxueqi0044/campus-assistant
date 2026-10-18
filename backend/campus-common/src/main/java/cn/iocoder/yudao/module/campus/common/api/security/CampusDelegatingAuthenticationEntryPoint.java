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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/** 校园路径使用冻结的 401 契约，其他路径原样委托芋道处理器。 */
public final class CampusDelegatingAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint delegate;

    public CampusDelegatingAuthenticationEntryPoint(AuthenticationEntryPoint delegate) {
        this.delegate = delegate;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        if (!CampusPathMatcher.isCampusPath(request)) {
            delegate.commence(request, response, exception);
            return;
        }
        writeCampusError(request, response);
    }

    private static void writeCampusError(HttpServletRequest request, HttpServletResponse response) {
        String traceId = CampusTraceContext.traceIdOf(request);
        CampusResponse<CampusErrorDetail> body = CampusResponse.error(CampusErrorCodes.AUTH_REQUIRED,
                CampusErrorDetail.of(CampusErrorCodes.AUTH_REQUIRED.getRetryAction()));
        body.setTraceId(traceId);
        response.setStatus(CampusErrorCodes.AUTH_REQUIRED.getHttpStatusValue());
        response.setHeader(CampusApiContract.HEADER_TRACE_ID, traceId);
        ServletUtils.writeJSON(response, body);
    }

}
