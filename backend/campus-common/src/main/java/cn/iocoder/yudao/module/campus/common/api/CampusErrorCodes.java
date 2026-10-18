package cn.iocoder.yudao.module.campus.common.api;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * M00.02 校园自有错误码登记表（冻结）。
 *
 * <p>编码规则沿用上游 10 位约定：{@code 1_040_000_xxx}。
 * <ul>
 *     <li>第一段 {@code 1}：业务级异常，与上游 {@code ServiceErrorCodeRange} 一致；</li>
 *     <li>第二段 {@code 040}：校园助手自有系统段，上游未占用 {@code 001~022} 之外的段位；</li>
 *     <li>第三段：子模块编号，M00 使用 {@code 000}，后续模块依次为 {@code 001}、{@code 002}…；</li>
 *     <li>第四段：模块内自增序号。</li>
 * </ul>
 *
 * <p>既有 HTTP 语义编码（400/401/403/404/429/500）直接复用上游
 * {@link GlobalErrorCodeConstants}，避免同一语义出现两个数值。
 *
 * <p>变更规则：新增条目必须同时更新 {@code contracts/openapi/campus-error-codes.csv}；
 * 删除或改数值属于破坏性变更，需要提升 {@link CampusApiContract#VERSION}。
 */
public interface CampusErrorCodes {

    // ========== 客户端错误（400 / 401 / 403 / 404 / 409 / 429） ==========

    CampusErrorSpec REQUEST_INVALID = CampusErrorSpec.of("CAMPUS_REQUEST_INVALID",
            new ErrorCode(1_040_000_000, "请求参数不正确"), HttpStatus.BAD_REQUEST, CampusRetryAction.USER_FIX);

    CampusErrorSpec MONEY_FORMAT_INVALID = CampusErrorSpec.of("CAMPUS_MONEY_FORMAT_INVALID",
            new ErrorCode(1_040_000_001, "金额必须使用整数分"), HttpStatus.BAD_REQUEST, CampusRetryAction.USER_FIX);

    CampusErrorSpec PAGE_SIZE_EXCEEDED = CampusErrorSpec.of("CAMPUS_PAGE_SIZE_EXCEEDED",
            new ErrorCode(1_040_000_002, "每页条数超过上限 100"), HttpStatus.BAD_REQUEST, CampusRetryAction.USER_FIX);

    CampusErrorSpec VERSION_CONFLICT = CampusErrorSpec.of("CAMPUS_VERSION_CONFLICT",
            new ErrorCode(1_040_000_010, "数据已被其他操作修改，请刷新后重试"),
            HttpStatus.CONFLICT, CampusRetryAction.USER_FIX);

    CampusErrorSpec IDEMPOTENCY_CONFLICT = CampusErrorSpec.of("CAMPUS_IDEMPOTENCY_CONFLICT",
            new ErrorCode(1_040_000_011, "同一幂等键的请求内容与首次不一致"),
            HttpStatus.CONFLICT, CampusRetryAction.USER_FIX);

    CampusErrorSpec COMMAND_IN_PROGRESS = CampusErrorSpec.of("CAMPUS_COMMAND_IN_PROGRESS",
            new ErrorCode(1_040_000_012, "同一幂等键的命令正在处理中，请稍后查询结果"),
            HttpStatus.CONFLICT, CampusRetryAction.RETRY);

    CampusErrorSpec RETRYABLE_CONFLICT = CampusErrorSpec.of("CAMPUS_RETRYABLE_CONFLICT",
            new ErrorCode(1_040_000_013, "并发冲突重试已达上限，请稍后查询或重试"),
            HttpStatus.CONFLICT, CampusRetryAction.RETRY);

    CampusErrorSpec RESOURCE_NOT_FOUND = CampusErrorSpec.of("CAMPUS_RESOURCE_NOT_FOUND",
            new ErrorCode(1_040_000_020, "资源不存在"), HttpStatus.NOT_FOUND, CampusRetryAction.USER_FIX);

    CampusErrorSpec AUTH_REQUIRED = CampusErrorSpec.of("CAMPUS_AUTH_REQUIRED",
            new ErrorCode(1_040_000_030, "账号未登录"), HttpStatus.UNAUTHORIZED, CampusRetryAction.USER_FIX);

    CampusErrorSpec PERMISSION_DENIED = CampusErrorSpec.of("CAMPUS_PERMISSION_DENIED",
            new ErrorCode(1_040_000_031, "没有该操作权限"), HttpStatus.FORBIDDEN, CampusRetryAction.USER_FIX);

    CampusErrorSpec RATE_LIMITED = CampusErrorSpec.of("CAMPUS_RATE_LIMITED",
            new ErrorCode(1_040_000_040, "请求过于频繁，请稍后重试"),
            HttpStatus.TOO_MANY_REQUESTS, CampusRetryAction.RETRY);

    // ========== M01.02 学校/校区公开浏览上下文 ===========

    CampusErrorSpec CAMPUS_SCHOOL_MISMATCH = CampusErrorSpec.of("CAMPUS_SCHOOL_MISMATCH",
            new ErrorCode(1_040_001_000, "校区不属于所选学校"), HttpStatus.BAD_REQUEST, CampusRetryAction.USER_FIX);

    CampusErrorSpec CAMPUS_UNAVAILABLE = CampusErrorSpec.of("CAMPUS_UNAVAILABLE",
            new ErrorCode(1_040_001_001, "所选学校或校区当前不可用"), HttpStatus.CONFLICT, CampusRetryAction.USER_FIX);

    // ========== M01.01 微信登录与身份绑定 ===========

    CampusErrorSpec WECHAT_CODE_INVALID = CampusErrorSpec.of("WECHAT_CODE_INVALID",
            new ErrorCode(1_040_001_010, "微信登录凭证无效或已过期"),
            HttpStatus.BAD_REQUEST, CampusRetryAction.USER_FIX);

    CampusErrorSpec IDENTITY_PROVIDER_UNAVAILABLE = CampusErrorSpec.of("IDENTITY_PROVIDER_UNAVAILABLE",
            new ErrorCode(1_040_001_011, "微信身份服务暂不可用，请稍后重试"),
            HttpStatus.SERVICE_UNAVAILABLE, CampusRetryAction.RETRY);

    CampusErrorSpec ACCOUNT_DISABLED = CampusErrorSpec.of("ACCOUNT_DISABLED",
            new ErrorCode(1_040_001_012, "账号已停用"),
            HttpStatus.FORBIDDEN, CampusRetryAction.USER_FIX);

    CampusErrorSpec IDENTITY_BINDING_CONFLICT = CampusErrorSpec.of("IDENTITY_BINDING_CONFLICT",
            new ErrorCode(1_040_001_013, "身份关联冲突，请联系运营处理"),
            HttpStatus.CONFLICT, CampusRetryAction.OPERATOR);

    // ========== 事件契约（消费者可观察失败） ==========

    CampusErrorSpec EVENT_ENVELOPE_INVALID = CampusErrorSpec.of("CAMPUS_EVENT_ENVELOPE_INVALID",
            new ErrorCode(1_040_000_050, "事件信封缺少必需字段：{}"),
            HttpStatus.BAD_REQUEST, CampusRetryAction.OPERATOR);

    CampusErrorSpec EVENT_PAYLOAD_FORBIDDEN = CampusErrorSpec.of("CAMPUS_EVENT_PAYLOAD_FORBIDDEN",
            new ErrorCode(1_040_000_051, "事件载荷包含禁止的敏感字段：{}"),
            HttpStatus.BAD_REQUEST, CampusRetryAction.OPERATOR);

    CampusErrorSpec EVENT_VERSION_UNSUPPORTED = CampusErrorSpec.of("CAMPUS_EVENT_VERSION_UNSUPPORTED",
            new ErrorCode(1_040_000_052, "事件类型或版本未被当前消费者支持：{}"),
            HttpStatus.NOT_IMPLEMENTED, CampusRetryAction.OPERATOR);

    CampusErrorSpec OUTBOX_EVENT_ID_CONFLICT = CampusErrorSpec.of("CAMPUS_OUTBOX_EVENT_ID_CONFLICT",
            new ErrorCode(1_040_000_053, "同一事件编号对应的事件内容不一致"),
            HttpStatus.CONFLICT, CampusRetryAction.OPERATOR);

    CampusErrorSpec OUTBOX_LEASE_LOST = CampusErrorSpec.of("CAMPUS_OUTBOX_LEASE_LOST",
            new ErrorCode(1_040_000_054, "事件处理租约已失效或被其他工作节点接管"),
            HttpStatus.CONFLICT, CampusRetryAction.RETRY);

    CampusErrorSpec OUTBOX_RETRY_EXHAUSTED = CampusErrorSpec.of("CAMPUS_OUTBOX_RETRY_EXHAUSTED",
            new ErrorCode(1_040_000_055, "事件处理重试已达上限，需要人工处理"),
            HttpStatus.INTERNAL_SERVER_ERROR, CampusRetryAction.OPERATOR);

    CampusErrorSpec OUTBOX_REPLAY_CONFLICT = CampusErrorSpec.of("CAMPUS_OUTBOX_REPLAY_CONFLICT",
            new ErrorCode(1_040_000_056, "事件状态已变化，请刷新后再执行重放"),
            HttpStatus.CONFLICT, CampusRetryAction.USER_FIX);

    // ========== 服务端兜底 ==========

    CampusErrorSpec INTERNAL_ERROR = CampusErrorSpec.of("CAMPUS_INTERNAL_ERROR",
            GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
            CampusRetryAction.RETRY);

    /** 全部登记条目；新增错误码必须加入本列表，契约一致性用例据此校验。 */
    List<CampusErrorSpec> ALL = List.of(
            REQUEST_INVALID,
            MONEY_FORMAT_INVALID,
            PAGE_SIZE_EXCEEDED,
            VERSION_CONFLICT,
            IDEMPOTENCY_CONFLICT,
            COMMAND_IN_PROGRESS,
            RETRYABLE_CONFLICT,
            RESOURCE_NOT_FOUND,
            AUTH_REQUIRED,
            PERMISSION_DENIED,
            RATE_LIMITED,
            CAMPUS_SCHOOL_MISMATCH,
            CAMPUS_UNAVAILABLE,
            WECHAT_CODE_INVALID,
            IDENTITY_PROVIDER_UNAVAILABLE,
            ACCOUNT_DISABLED,
            IDENTITY_BINDING_CONFLICT,
            EVENT_ENVELOPE_INVALID,
            EVENT_PAYLOAD_FORBIDDEN,
            EVENT_VERSION_UNSUPPORTED,
            OUTBOX_EVENT_ID_CONFLICT,
            OUTBOX_LEASE_LOST,
            OUTBOX_RETRY_EXHAUSTED,
            OUTBOX_REPLAY_CONFLICT,
            INTERNAL_ERROR);

}
