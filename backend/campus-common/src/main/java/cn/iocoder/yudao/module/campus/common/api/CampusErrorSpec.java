package cn.iocoder.yudao.module.campus.common.api;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * M00.02 错误规范条目：稳定名称 + 数值编码 + HTTP 状态 + 可采取动作。
 *
 * <p>数值编码冻结在 {@link CampusErrorCodes}，并与 {@code contracts/openapi/campus-error-codes.csv}
 * 逐条对应；两者由自动化用例校验，不能只在文档里写一套。
 */
@Getter
public final class CampusErrorSpec {

    /** 稳定错误名称，供契约文档、事件审计与前端分支使用 */
    private final String name;

    /** 上游 {@link ErrorCode} 载体：数值编码 + 可格式化消息模板 */
    private final ErrorCode errorCode;

    /** 对外 HTTP 状态码，见 M00.02「接口契约」的映射表 */
    private final HttpStatus httpStatus;

    /** 调用方应当采取的动作 */
    private final CampusRetryAction retryAction;

    private CampusErrorSpec(String name, ErrorCode errorCode, HttpStatus httpStatus, CampusRetryAction retryAction) {
        this.name = name;
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.retryAction = retryAction;
    }

    public static CampusErrorSpec of(String name, ErrorCode errorCode, HttpStatus httpStatus,
                                     CampusRetryAction retryAction) {
        return new CampusErrorSpec(name, errorCode, httpStatus, retryAction);
    }

    public Integer getCode() {
        return errorCode.getCode();
    }

    /** 消息模板，占位符为 {@code {}}，与上游 {@code ServiceExceptionUtil#doFormat} 一致 */
    public String getMessagePattern() {
        return errorCode.getMsg();
    }

    public int getHttpStatusValue() {
        return httpStatus.value();
    }

    /** 是否允许调用方原样重试 */
    public boolean isRetryable() {
        return retryAction == CampusRetryAction.RETRY;
    }

}
