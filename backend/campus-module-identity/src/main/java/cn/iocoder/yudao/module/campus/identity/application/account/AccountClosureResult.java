package cn.iocoder.yudao.module.campus.identity.application.account;

import cn.iocoder.yudao.module.campus.identity.api.AccountLifecycleStatus;
import cn.iocoder.yudao.module.campus.identity.application.port.AccountObligation;

import java.util.List;

public record AccountClosureResult(long userId, AccountLifecycleStatus status,
                                   long authVersion, long version,
                                   List<AccountObligation> blockers, String nextAction) {

    public AccountClosureResult {
        blockers = List.copyOf(blockers);
    }
}
