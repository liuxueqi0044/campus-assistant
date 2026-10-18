package cn.iocoder.yudao.module.campus.common.api;

import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

/**
 * M00.02 校园 API 统一响应：{@code code / message / data / traceId}。
 *
 * <p>为什么不复用上游 {@code CommonResult}：上游结构为 {@code code/msg/data}，且被
 * system、infra 等上游模块的既有前端依赖。M00.02 要求校园入口固定
 * {@code code/message/data/traceId}，因此校园入口使用本类型，上游入口保持原样，
 * 两者边界在 {@link CampusApiContract#APP_API_PREFIX} 与
 * {@link CampusApiContract#ADMIN_API_PREFIX}。
 *
 * <p>成功响应同样带 {@code traceId}，便于前端把用户截图直接对应到服务端链路。
 *
 * @param <T> 业务数据类型
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CampusResponse<T> implements Serializable {

    /** 业务码：0 表示成功；其余取 {@link CampusErrorCodes} 或上游全局码 */
    private Integer code;

    /** 用户可阅读的提示；成功时为空字符串 */
    private String message;

    /** 业务数据；错误时为 {@link CampusErrorDetail} 或 null */
    private T data;

    /** 链路追踪编号，始终非空 */
    private String traceId;

    public static <T> CampusResponse<T> success(T data) {
        CampusResponse<T> response = new CampusResponse<>();
        response.code = GlobalErrorCodeConstants.SUCCESS.getCode();
        response.message = "";
        response.data = data;
        response.traceId = CampusTraceContext.currentOrNewTraceId();
        return response;
    }

    /**
     * 构造错误响应。
     *
     * @param spec        错误规范条目
     * @param message     已格式化的错误提示；传 null 时使用条目模板
     * @param errorDetail 恢复信息；传 null 时按条目动作生成
     */
    public static <T> CampusResponse<T> error(CampusErrorSpec spec, String message, T errorDetail) {
        CampusResponse<T> response = new CampusResponse<>();
        response.code = spec.getCode();
        response.message = message != null ? message : spec.getMessagePattern();
        response.data = errorDetail;
        response.traceId = CampusTraceContext.currentOrNewTraceId();
        return response;
    }

    public static <T> CampusResponse<T> error(CampusErrorSpec spec, T errorDetail) {
        return error(spec, spec.getMessagePattern(), errorDetail);
    }

    @JsonIgnore
    public boolean isSuccess() {
        return Objects.equals(code, GlobalErrorCodeConstants.SUCCESS.getCode());
    }

}
