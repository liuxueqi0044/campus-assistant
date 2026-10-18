package cn.iocoder.yudao.module.campus.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * M00.02 分页参数：默认值与硬上限在此冻结。
 *
 * <p>上限只允许通过契约版本升级调整；导出等需要全量的场景必须走独立接口与独立授权，
 * 不能靠把 {@code pageSize} 设为 -1 绕过。
 */
@Data
public class CampusPageParam implements Serializable {

    /** 默认页码 */
    public static final int DEFAULT_PAGE_NO = 1;

    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数硬上限 */
    public static final int MAX_PAGE_SIZE = 100;

    @Min(value = 1, message = "页码最小值为 1")
    private Integer pageNo = DEFAULT_PAGE_NO;

    @Min(value = 1, message = "每页条数最小值为 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数最大值为 100")
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /**
     * 在不经过 Bean Validation 的调用路径（内部调用、导入任务）上补齐边界检查，
     * 返回冻结错误码 {@link CampusErrorCodes#PAGE_SIZE_EXCEEDED} 而不是静默放大查询量。
     */
    public CampusPageParam enforceLimits() {
        if (pageNo == null || pageNo < 1) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID,
                    CampusErrorDetail.of(CampusRetryAction.USER_FIX)
                            .withFieldErrors(List.of(new CampusFieldError("pageNo", "页码最小值为 1"))));
        }
        if (pageSize == null || pageSize < 1) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID,
                    CampusErrorDetail.of(CampusRetryAction.USER_FIX)
                            .withFieldErrors(List.of(new CampusFieldError("pageSize", "每页条数最小值为 1"))));
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new CampusApiException(CampusErrorCodes.PAGE_SIZE_EXCEEDED,
                    CampusErrorDetail.of(CampusRetryAction.USER_FIX)
                            .withFieldErrors(List.of(new CampusFieldError("pageSize",
                                    "每页条数最大值为 " + MAX_PAGE_SIZE))));
        }
        return this;
    }

}
