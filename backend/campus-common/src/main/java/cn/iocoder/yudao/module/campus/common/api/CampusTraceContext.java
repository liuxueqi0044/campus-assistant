package cn.iocoder.yudao.module.campus.common.api;

import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * M00.02 链路追踪上下文：为每个校园请求提供稳定且始终存在的 traceId。
 *
 * <p>取值顺序：请求属性（{@link CampusTraceFilter} 或调用方显式绑定）→ 服务端 OpenTelemetry
 * 链路号 → 新生成。生成值写入请求属性与 MDC；原始客户端的 {@code X-Trace-Id}
 * 不作为可信链路身份，避免调用方伪造或碰撞其他请求的排障编号。
 * {@link CampusApiContract#HEADER_TRACE_ID}，让「无效参数与未捕获异常均带 traceId」既有响应体证据
 * 也有服务端日志证据。
 */
public final class CampusTraceContext {

    /** 请求属性名，避免与上游 {@code common_result} 等属性冲突 */
    public static final String REQUEST_ATTRIBUTE_TRACE_ID = "campus_trace_id";

    /** MDC 键名，日志 pattern 可用 {@code %X{campusTraceId}} 输出 */
    public static final String MDC_KEY = "campusTraceId";

    /** 允许透传的调用方追踪号：仅数字、字母、点、下划线与连字符 */
    private static final String TRACE_ID_PATTERN = "[A-Za-z0-9._-]{8,64}";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CampusTraceContext() {
    }

    /** 生成 32 位十六进制追踪号 */
    public static String generateTraceId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** 判断调用方传入的追踪号是否可用；不可用时必须重新生成，避免日志注入 */
    public static boolean isAcceptable(String traceId) {
        return traceId != null && traceId.matches(TRACE_ID_PATTERN);
    }

    /**
     * 获得当前请求的追踪号；没有则生成并绑定到当前请求。
     */
    public static String currentOrNewTraceId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            String traced = TracerUtils.getTraceId();
            return isAcceptable(traced) ? traced : generateTraceId();
        }
        return traceIdOf(request);
    }

    /** 取得（必要时生成）指定请求的追踪号，并写入请求属性与 MDC */
    public static String traceIdOf(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ATTRIBUTE_TRACE_ID);
        if (existing instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String fromTracer = TracerUtils.getTraceId();
        String traceId = fromTracer != null && !fromTracer.isBlank() ? fromTracer : generateTraceId();
        bind(request, traceId);
        return traceId;
    }

    /** 使用服务端活动链路号；没有活动链路时生成新编号。 */
    public static String bindServerTrace(HttpServletRequest request) {
        String traced = TracerUtils.getTraceId();
        return bind(request, isAcceptable(traced) ? traced : null);
    }

    /**
     * 绑定内部已信任的追踪号到请求属性与 MDC；仅供服务端链路集成和测试使用，
     * 不能直接传入未经信任边界处理的请求头。
     */
    public static String bind(HttpServletRequest request, String candidate) {
        String traceId = isAcceptable(candidate) ? candidate : generateTraceId();
        request.setAttribute(REQUEST_ATTRIBUTE_TRACE_ID, traceId);
        MDC.put(MDC_KEY, traceId);
        return traceId;
    }

    /** 清理 MDC，必须在请求结束时调用，避免线程复用导致追踪号串线 */
    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    public static String currentTraceId() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            Object existing = request.getAttribute(REQUEST_ATTRIBUTE_TRACE_ID);
            if (existing instanceof String traceId && !traceId.isBlank()) {
                return traceId;
            }
        }
        String mdc = MDC.get(MDC_KEY);
        return mdc != null ? mdc : "";
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

}
