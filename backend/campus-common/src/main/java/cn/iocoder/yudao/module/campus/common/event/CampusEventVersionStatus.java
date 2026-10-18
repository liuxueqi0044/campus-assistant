package cn.iocoder.yudao.module.campus.common.event;

/**
 * 事件类型/版本对当前消费者的支持情况。
 *
 * <p>未知版本必须是「可观察失败」，不允许按旧结构猜测执行。
 */
public enum CampusEventVersionStatus {

    /** 该类型与版本已有处理逻辑 */
    SUPPORTED,

    /** 类型已知，但版本超出消费者支持范围 */
    UNSUPPORTED_VERSION,

    /** 类型本身未知 */
    UNKNOWN_TYPE

}
