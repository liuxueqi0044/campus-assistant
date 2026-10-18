package cn.iocoder.yudao.module.campus.common.api;

/**
 * M00.02 公共 API 契约常量（冻结值，不得随业务需求随意变更）。
 *
 * <p>契约版本与 {@code contracts/} 目录下的 OpenAPI 文档、事件 Schema 保持一致；
 * 变更字段含义或删除字段必须先提升契约版本并给出迁移窗口。
 */
public final class CampusApiContract {

    /**
     * 契约版本。与 {@code contracts/README.md}、{@code contracts/openapi/*.yaml} 中登记的值一致。
     */
    public static final String VERSION = "2026.08-campus.1";

    /** 学生端入口前缀，见 M00.02「接口契约」 */
    public static final String APP_API_PREFIX = "/app-api/campus/v1";

    /** 管理端入口前缀 */
    public static final String ADMIN_API_PREFIX = "/admin-api/campus/v1";

    /** 链路追踪编号响应头；响应体中的 {@code traceId} 与之一致 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** 敏感写请求的幂等键请求头（M00.04 使用） */
    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    /** 金额单位：整数分。JSON 中必须是不带小数的数字，禁止字符串与浮点。 */
    public static final String MONEY_UNIT = "CNY_FEN";

    /** 标识字段在 JSON 中固定为字符串，避免 JavaScript 安全整数溢出 */
    public static final String ID_JSON_TYPE = "string";

    /** 时间字段格式：RFC 3339 且必须带时区偏移，例如 {@code 2026-09-17T10:15:30+08:00} */
    public static final String TIME_FORMAT = "RFC3339_WITH_OFFSET";

    private CampusApiContract() {
    }

}
