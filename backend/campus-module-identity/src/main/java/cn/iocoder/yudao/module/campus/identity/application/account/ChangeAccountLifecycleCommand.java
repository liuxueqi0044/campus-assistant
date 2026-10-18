package cn.iocoder.yudao.module.campus.identity.application.account;

import cn.iocoder.yudao.module.campus.identity.api.AccountLifecycleStatus;

public record ChangeAccountLifecycleCommand(long userId, AccountLifecycleStatus targetStatus,
                                            String reason, long expectedVersion) {
}
