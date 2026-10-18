package cn.iocoder.yudao.module.campus.identity.application.port;

import java.util.List;

public interface AccountObligationPort {

    List<AccountObligation> findBlockers(long userId);
}
