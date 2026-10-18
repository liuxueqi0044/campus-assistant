package cn.iocoder.yudao.module.campus.common.api;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;

/**
 * M00.02 校园业务异常：携带冻结的错误规范条目与可选恢复信息。
 *
 * <p>领域代码抛出本异常，由 {@link CampusExceptionHandler} 统一映射成 HTTP 状态 + 契约响应，
 * 避免每个控制器各自拼错误结构或直接把内部异常暴露给调用方。
 */
public class CampusApiException extends RuntimeException {

    private final CampusErrorSpec spec;

    private final CampusErrorDetail detail;

    public CampusApiException(CampusErrorSpec spec) {
        this(spec, null);
    }

    public CampusApiException(CampusErrorSpec spec, CampusErrorDetail detail, Object... messageParams) {
        super(ServiceExceptionUtil.doFormat(spec.getCode(), spec.getMessagePattern(), messageParams));
        this.spec = spec;
        this.detail = detail != null ? detail : CampusErrorDetail.of(spec.getRetryAction());
    }

    public CampusErrorSpec getSpec() {
        return spec;
    }

    public CampusErrorDetail getDetail() {
        return detail;
    }

    public Integer getCode() {
        return spec.getCode();
    }

}
