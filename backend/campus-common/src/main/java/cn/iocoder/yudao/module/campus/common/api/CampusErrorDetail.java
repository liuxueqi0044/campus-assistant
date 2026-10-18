package cn.iocoder.yudao.module.campus.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * M00.02 错误响应的 {@code data} 部分。
 *
 * <p>只包含调用方恢复所需的最小信息：是否可重试、需要谁修正、字段级原因、冲突时的当前版本与状态。
 * 不含请求体原文、内部类名、堆栈或数据库细节。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CampusErrorDetail implements Serializable {

    /** 是否允许原样重试 */
    private Boolean retryable;

    /** 调用方应当采取的动作 */
    private CampusRetryAction requiredAction;

    /** 参数错误明细 */
    private List<CampusFieldError> fieldErrors;

    /** 409 冲突时的当前版本，便于调用方基于最新版本重试 */
    private String currentVersion;

    /** 409 冲突时的当前状态 */
    private String currentState;

    public static CampusErrorDetail of(CampusRetryAction action) {
        CampusErrorDetail detail = new CampusErrorDetail();
        detail.retryable = action == CampusRetryAction.RETRY;
        detail.requiredAction = action;
        return detail;
    }

    public CampusErrorDetail withFieldErrors(List<CampusFieldError> errors) {
        this.fieldErrors = errors == null ? null : new ArrayList<>(errors);
        return this;
    }

    public CampusErrorDetail withConflict(String currentVersion, String currentState) {
        this.currentVersion = currentVersion;
        this.currentState = currentState;
        return this;
    }

}
