package cn.iocoder.yudao.module.campus.common.api;

/**
 * M00.02 错误处理提示：调用方拿到错误后应当采取的动作。
 *
 * <p>契约要求「重试提示区分可重试与需用户修正」，因此不允许把可重试冲突与业务规则拒绝混为一谈。
 */
public enum CampusRetryAction {

    /** 原样重试即可（例如限流、命令处理中） */
    RETRY,

    /** 必须由用户修正输入或刷新版本后重新提交 */
    USER_FIX,

    /** 需要运营或值守人员处理的内部问题（例如事件版本不认识） */
    OPERATOR

}
