package cn.iocoder.yudao.module.campus.common.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * M00.02 未匹配地址的 404 契约响应。
 *
 * <p>为什么不放进 {@link CampusExceptionHandler}：该类按包限定，而「没有匹配到控制器」时
 * Spring 找不到控制器，包限定条件不成立，处理器不会生效。因此这里用不限包的处理器，
 * 并在方法内按 URL 前缀自行判定范围。
 *
 * <p>非校园入口（上游 {@code /admin-api/system} 等）必须保持上游原有行为，
 * 处理方法是重新抛出异常交回上游链路，而不是替上游改写响应结构。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class CampusUnmappedPathExceptionHandler {

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<CampusResponse<CampusErrorDetail>> handleUnmappedPath(Exception ex,
                                                                                 HttpServletRequest request)
            throws Exception {
        if (!isCampusPath(request)) {
            // 交回上游处理器，保持上游入口行为不变
            throw ex;
        }
        log.warn("[handleUnmappedPath][uri({}) traceId({})]", request.getRequestURI(),
                CampusTraceContext.currentTraceId());
        CampusErrorSpec spec = CampusErrorCodes.RESOURCE_NOT_FOUND;
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(CampusResponse.error(spec, spec.getMessagePattern(),
                        CampusErrorDetail.of(spec.getRetryAction())));
    }

    /** 校园入口：学生端、管理端前缀，以及 M00.01 的就绪探测路径 */
    public static boolean isCampusPath(HttpServletRequest request) {
        return CampusPathMatcher.isCampusPath(request);
    }

}
