package cn.iocoder.yudao.module.campus.identity.api;

public record AccountLifecycleSnapshot(long userId, AccountLifecycleStatus status,
                                       long authVersion, long version) {
}
