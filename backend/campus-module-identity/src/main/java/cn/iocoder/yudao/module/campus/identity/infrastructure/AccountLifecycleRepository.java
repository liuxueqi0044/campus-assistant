package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.AccountLifecycleDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.AccountLifecycleLogDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql.AccountLifecycleLogMapper;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql.AccountLifecycleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class AccountLifecycleRepository {

    private final AccountLifecycleMapper lifecycleMapper;
    private final AccountLifecycleLogMapper logMapper;

    public AccountLifecycleDO find(long userId) {
        return lifecycleMapper.selectLifecycle(userId);
    }

    public AccountLifecycleDO lock(long userId) {
        return lifecycleMapper.selectLifecycleForUpdate(userId);
    }

    public void insert(AccountLifecycleDO row) {
        lifecycleMapper.insert(row);
    }

    public boolean transition(long userId, String sourceStatus, String targetStatus,
                              long nextAuthVersion, long expectedVersion, long nextVersion,
                              String reason, LocalDateTime closureRequestedAt, LocalDateTime closedAt,
                              String actorType, Long actorId) {
        return lifecycleMapper.transition(userId, sourceStatus, targetStatus, nextAuthVersion,
                expectedVersion, nextVersion, reason, closureRequestedAt, closedAt, actorType, actorId) == 1;
    }

    public void appendLog(AccountLifecycleLogDO log) {
        logMapper.insert(log);
    }

    public boolean markSessionRevoked(long userId, long authVersion) {
        return lifecycleMapper.markSessionRevoked(userId, authVersion) == 1;
    }
}
