package cn.iocoder.yudao.module.campus.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * M00.02 追踪号入口：第一个进入 Servlet 链的校园过滤器。
 *
 * <p>只处理校园 API 与 readiness，不改变 system／infra 等上游入口。原始客户端提供的
 * {@link CampusApiContract#HEADER_TRACE_ID} 不被当作可信链路身份；网关应通过标准 trace context
 * 建立 OpenTelemetry 链路，本过滤器优先使用该服务端链路号，否则自行生成。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CampusTraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = CampusTraceContext.bindServerTrace(request);
        response.setHeader(CampusApiContract.HEADER_TRACE_ID, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            CampusTraceContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !CampusPathMatcher.isCampusPath(request);
    }

}
