package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.OperatorGrantAuditDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.OperatorGrantDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql.OperatorAuthorizationVersionMapper;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql.OperatorGrantAuditMapper;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql.OperatorGrantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OperatorGrantRepository {

    private final OperatorGrantMapper grantMapper;
    private final OperatorGrantAuditMapper auditMapper;
    private final OperatorAuthorizationVersionMapper versionMapper;

    public List<OperatorGrantDO> findActiveGrants(long operatorId) {
        return grantMapper.selectActiveByOperator(operatorId);
    }

    public OperatorGrantDO lockScope(long operatorId, String scopeType, Long schoolId, Long campusId) {
        return grantMapper.selectScopeForUpdate(operatorId, scopeType, schoolId, campusId);
    }

    public OperatorGrantDO lockGrant(long grantId) {
        return grantMapper.selectByIdForUpdate(grantId);
    }

    public void insertGrant(OperatorGrantDO grant) {
        grantMapper.insert(grant);
    }

    public boolean activateGrant(long id, String actionsJson, LocalDateTime validFrom, LocalDateTime expiresAt,
                                 long expectedVersion, long nextVersion, long actorId) {
        return grantMapper.activate(id, actionsJson, validFrom, expiresAt,
                expectedVersion, nextVersion, actorId) == 1;
    }

    public boolean revokeGrant(long id, long expectedVersion, long nextVersion, long actorId,
                               String reason, LocalDateTime revokedAt) {
        return grantMapper.revoke(id, expectedVersion, nextVersion, actorId, reason, revokedAt) == 1;
    }

    public void appendAudit(OperatorGrantAuditDO audit) {
        auditMapper.insert(audit);
    }

    public long bumpAuthorizationVersion(long operatorId) {
        versionMapper.bump(operatorId);
        Long version = versionMapper.selectVersion(operatorId);
        if (version == null || version <= 0) {
            throw new IllegalStateException("operator authorization version was not persisted");
        }
        return version;
    }

    public long authorizationVersion(long operatorId) {
        Long version = versionMapper.selectVersion(operatorId);
        return version == null ? 0L : version;
    }
}
