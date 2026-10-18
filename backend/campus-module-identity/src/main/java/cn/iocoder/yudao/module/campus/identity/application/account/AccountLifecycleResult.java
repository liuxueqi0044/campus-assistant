package cn.iocoder.yudao.module.campus.identity.application.account;

import cn.iocoder.yudao.module.campus.identity.api.AccountLifecycleStatus;

public record AccountLifecycleResult(long userId, AccountLifecycleStatus status,
                                     long authVersion, long version,
                                     String sessionRevocationState) {
}
