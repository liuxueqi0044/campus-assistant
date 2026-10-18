package cn.iocoder.yudao.module.campus.identity.application.authorization;

import cn.iocoder.yudao.module.campus.identity.api.OperatorAction;
import cn.iocoder.yudao.module.campus.identity.domain.authorization.OperatorScopeType;

import java.time.Instant;
import java.util.Set;

/** expectedVersion=0 creates a new scope; positive values update/restore its existing row. */
public record GrantOperatorCommand(long operatorId, OperatorScopeType scopeType,
                                   Long schoolId, Long campusId, Set<OperatorAction> actions,
                                   Instant validFrom, Instant expiresAt,
                                   long expectedVersion, String reason) {
}
