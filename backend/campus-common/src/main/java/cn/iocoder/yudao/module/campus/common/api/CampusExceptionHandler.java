package cn.iocoder.yudao.module.campus.common.api;

import cn.iocoder.yudao.module.campus.common.api.json.CampusMoneyFormatException;
import cn.iocoder.yudao.module.campus.common.api.json.CampusIdFormatException;
import cn.iocoder.yudao.module.campus.common.api.json.CampusTimestampFormatException;
import cn.iocoder.yudao.module.campus.common.transaction.TransactionRetryExhaustedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;

/**
 * M00.02 校园入口统一异常映射。
 *
 * <p>只作用于 {@code cn.iocoder.yudao.module.campus} 下的控制器，上游 system/infra 等入口保持原行为。
 * 顺序为最高优先级，确保覆盖上游 {@code GlobalExceptionHandler} 对同类异常的处理。
 *
 * <p>输出约束：
 * <ul>
 *     <li>HTTP 状态与 {@link CampusErrorSpec#getHttpStatus()} 一致，不再一律返回 200；</li>
 *     <li>响应体只含 {@code code/message/data/traceId}，不含异常类名与堆栈；</li>
 *     <li>未捕获异常在服务端记录堆栈，对外只给通用提示与 traceId。</li>
 * </ul>
 */
@RestControllerAdvice(basePackages = "cn.iocoder.yudao.module.campus")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class CampusExceptionHandler {

    @ExceptionHandler(CampusApiException.class)
    public ResponseEntity<CampusResponse<CampusErrorDetail>> handleCampusApiException(CampusApiException ex) {
        CampusErrorSpec spec = ex.getSpec();
        log.warn("[handleCampusApiException][code({}) traceId({})]", spec.getCode(), CampusTraceContext.currentTraceId());
        return build(spec, ex.getMessage(), ex.getDetail());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CampusResponse<CampusErrorDetail>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        return buildInvalidRequest(fieldErrorsOf(ex.getBindingResult().getAllErrors()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<CampusResponse<CampusErrorDetail>> handleBindException(BindException ex) {
        return buildInvalidRequest(fieldErrorsOf(ex.getAllErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CampusResponse<CampusErrorDetail>> handleConstraintViolation(
            ConstraintViolationException ex) {
        List<CampusFieldError> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.add(new CampusFieldError(String.valueOf(violation.getPropertyPath()), violation.getMessage()));
        }
        return buildInvalidRequest(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CampusResponse<CampusErrorDetail>> handleMissingParameter(
            MissingServletRequestParameterException ex) {
        return buildInvalidRequest(List.of(new CampusFieldError(ex.getParameterName(), "请求参数缺失")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CampusResponse<CampusErrorDetail>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        return buildInvalidRequest(List.of(new CampusFieldError(ex.getName(), "请求参数类型错误")));
    }

    /**
     * 请求体不可读：金额使用小数、字段类型不符、JSON 结构损坏等。
     *
     * <p>金额格式错误给出专用错误码，其余给通用参数错误；两种都不回显原始请求体。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CampusResponse<CampusErrorDetail>> handleMessageNotReadable(
            HttpMessageNotReadableException ex) {
        if (containsCause(ex, CampusMoneyFormatException.class)) {
            CampusMoneyFormatException money = (CampusMoneyFormatException) findCause(ex,
                    CampusMoneyFormatException.class);
            CampusErrorDetail detail = CampusErrorDetail.of(CampusErrorCodes.MONEY_FORMAT_INVALID.getRetryAction())
                    .withFieldErrors(List.of(new CampusFieldError(money.getField(), "金额必须使用整数分")));
            return build(CampusErrorCodes.MONEY_FORMAT_INVALID, CampusErrorCodes.MONEY_FORMAT_INVALID.getMessagePattern(),
                    detail);
        }
        if (containsCause(ex, CampusIdFormatException.class)) {
            CampusIdFormatException id = (CampusIdFormatException) findCause(ex, CampusIdFormatException.class);
            return buildInvalidRequest(List.of(new CampusFieldError(id.getField(),
                    "标识字段必须使用 JSON 字符串")));
        }
        if (containsCause(ex, CampusTimestampFormatException.class)) {
            CampusTimestampFormatException timestamp = (CampusTimestampFormatException) findCause(ex,
                    CampusTimestampFormatException.class);
            return buildInvalidRequest(List.of(new CampusFieldError(timestamp.getField(),
                    "时间必须使用带时区偏移的 RFC 3339 格式")));
        }
        return buildInvalidRequest(List.of(new CampusFieldError("<request-body>", "请求体无法解析")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CampusResponse<CampusErrorDetail>> handleAccessDenied(AccessDeniedException ex) {
        return build(CampusErrorCodes.PERMISSION_DENIED, CampusErrorCodes.PERMISSION_DENIED.getMessagePattern(),
                CampusErrorDetail.of(CampusRetryAction.USER_FIX));
    }

    /** M00.03 有界数据库锁重试耗尽：不得伪报成功，也不向外暴露操作名和底层 SQL 异常。 */
    @ExceptionHandler(TransactionRetryExhaustedException.class)
    public ResponseEntity<CampusResponse<CampusErrorDetail>> handleTransactionRetryExhausted(
            TransactionRetryExhaustedException ex) {
        log.warn("[handleTransactionRetryExhausted][attempts({}) traceId({})]", ex.getAttempts(),
                CampusTraceContext.currentTraceId());
        return build(CampusErrorCodes.RETRYABLE_CONFLICT,
                CampusErrorCodes.RETRYABLE_CONFLICT.getMessagePattern(),
                CampusErrorDetail.of(CampusRetryAction.RETRY));
    }

    /**
     * 兜底：任何未捕获异常都在服务端记录堆栈，对外只给通用提示与 traceId。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CampusResponse<CampusErrorDetail>> handleUnexpectedException(Exception ex,
                                                                                        HttpServletRequest request) {
        log.error("[handleUnexpectedException][uri({}) traceId({})]", request.getRequestURI(),
                CampusTraceContext.currentTraceId(), ex);
        return build(CampusErrorCodes.INTERNAL_ERROR, CampusErrorCodes.INTERNAL_ERROR.getMessagePattern(),
                CampusErrorDetail.of(CampusRetryAction.RETRY));
    }

    private ResponseEntity<CampusResponse<CampusErrorDetail>> buildInvalidRequest(List<CampusFieldError> errors) {
        CampusErrorDetail detail = CampusErrorDetail.of(CampusErrorCodes.REQUEST_INVALID.getRetryAction())
                .withFieldErrors(errors);
        return build(CampusErrorCodes.REQUEST_INVALID, CampusErrorCodes.REQUEST_INVALID.getMessagePattern(), detail);
    }

    private ResponseEntity<CampusResponse<CampusErrorDetail>> build(CampusErrorSpec spec, String message,
                                                                   CampusErrorDetail detail) {
        HttpStatus status = spec.getHttpStatus();
        return ResponseEntity.status(status)
                .body(CampusResponse.error(spec, message, detail != null
                        ? detail : CampusErrorDetail.of(spec.getRetryAction())));
    }

    private static List<CampusFieldError> fieldErrorsOf(List<ObjectError> errors) {
        List<CampusFieldError> fieldErrors = new ArrayList<>();
        for (ObjectError error : errors) {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            fieldErrors.add(new CampusFieldError(field, error.getDefaultMessage()));
        }
        return fieldErrors;
    }

    private static boolean containsCause(Throwable throwable, Class<? extends Throwable> type) {
        return findCause(throwable, type) != null;
    }

    private static Throwable findCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return current;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return null;
    }

}
