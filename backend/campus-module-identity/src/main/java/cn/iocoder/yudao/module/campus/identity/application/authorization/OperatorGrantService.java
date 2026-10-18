package cn.iocoder.yudao.module.campus.identity.application.authorization;

import cn.iocoder.yudao.module.campus.identity.api.CampusScopeApi;
import cn.iocoder.yudao.module.campus.identity.api.OperatorAction;
import cn.iocoder.yudao.module.campus.identity.application.port.OperatorAccountPort;
import cn.iocoder.yudao.module.campus.identity.domain.CampusStatus;
import cn.iocoder.yudao.module.campus.identity.domain.authorization.OperatorGrant;
import cn.iocoder.yudao.module.campus.identity.domain.authorization.OperatorGrantStatus;
import cn.iocoder.yudao.module.campus.identity.domain.authorization.OperatorScopeType;
import cn.iocoder.yudao.module.campus.identity.infrastructure.OperatorGrantRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.SchoolRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.OperatorGrantAuditDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.OperatorGrantDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.SchoolDO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OperatorGrantService {

    private final OperatorGrantRepository repository;
    private final OperatorAccountPort operatorAccountPort;
    private final CampusScopeApi campusScopeApi;
    private final SchoolRepository schoolRepository;
    private final TransactionOperations transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OperatorGrantResult grant(long actorId, GrantOperatorCommand command) {
        Objects.requireNonNull(command, "command");
        ValidGrant input = validate(command);
        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                assertGrantManager(actorId);
                operatorAccountPort.assertActive(input.operatorId());
                validateDirectoryScope(input.scopeType(), input.schoolId(), input.campusId());
                OperatorGrantDO existing = repository.lockScope(input.operatorId(), input.scopeType().name(),
                        input.schoolId(), input.campusId());
                if (existing == null) {
                    if (input.expectedVersion() != 0) {
                        throw failure(OperatorAuthorizationFailure.GRANT_CONFLICT);
                    }
                    OperatorGrantDO created = new OperatorGrantDO();
                    created.setOperatorId(input.operatorId());
                    created.setScopeType(input.scopeType().name());
                    created.setSchoolId(input.schoolId());
                    created.setCampusId(input.campusId());
                    created.setActionsJson(input.actionsJson());
                    created.setStatus(OperatorGrantStatus.ACTIVE.name());
                    created.setValidFrom(local(input.validFrom()));
                    created.setExpiresAt(local(input.expiresAt()));
                    created.setVersion(1L);
                    created.setGrantedBy(actorId);
                    created.setCreator(String.valueOf(actorId));
                    created.setUpdater(String.valueOf(actorId));
                    repository.insertGrant(created);
                    appendAudit(created, "GRANT", "NONE", actorId, input.reason(), clock.instant());
                    long authorizationVersion = repository.bumpAuthorizationVersion(input.operatorId());
                    return new OperatorGrantResult(created.getId(), created.getStatus(), created.getVersion(),
                            authorizationVersion);
                }
                if (input.expectedVersion() <= 0 || existing.getVersion() != input.expectedVersion()) {
                    throw failure(OperatorAuthorizationFailure.GRANT_CONFLICT);
                }
                String fromStatus = existing.getStatus();
                long nextVersion = existing.getVersion() + 1;
                if (!repository.activateGrant(existing.getId(), input.actionsJson(), local(input.validFrom()),
                        local(input.expiresAt()), existing.getVersion(), nextVersion, actorId)) {
                    throw failure(OperatorAuthorizationFailure.GRANT_CONFLICT);
                }
                existing.setActionsJson(input.actionsJson());
                existing.setStatus(OperatorGrantStatus.ACTIVE.name());
                existing.setValidFrom(local(input.validFrom()));
                existing.setExpiresAt(local(input.expiresAt()));
                existing.setVersion(nextVersion);
                existing.setGrantedBy(actorId);
                existing.setRevokedAt(null);
                existing.setRevokedBy(null);
                existing.setRevocationReason(null);
                String auditAction = OperatorGrantStatus.REVOKED.name().equals(fromStatus) ? "RESTORE" : "UPDATE";
                appendAudit(existing, auditAction, fromStatus, actorId, input.reason(), clock.instant());
                long authorizationVersion = repository.bumpAuthorizationVersion(input.operatorId());
                return new OperatorGrantResult(existing.getId(), existing.getStatus(), existing.getVersion(),
                        authorizationVersion);
            }));
        } catch (DuplicateKeyException conflict) {
            throw failure(OperatorAuthorizationFailure.GRANT_CONFLICT);
        }
    }

    public OperatorGrantResult revoke(long actorId, RevokeOperatorGrantCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.grantId() <= 0 || command.expectedVersion() <= 0) {
            throw new IllegalArgumentException("grantId and expectedVersion must be positive");
        }
        String reason = requireReason(command.reason());
        return Objects.requireNonNull(transactions.execute(status -> {
            assertGrantManager(actorId);
            OperatorGrantDO existing = repository.lockGrant(command.grantId());
            if (existing == null) {
                throw failure(OperatorAuthorizationFailure.GRANT_NOT_FOUND);
            }
            if (!OperatorGrantStatus.ACTIVE.name().equals(existing.getStatus())
                    || existing.getVersion() != command.expectedVersion()) {
                throw failure(OperatorAuthorizationFailure.GRANT_CONFLICT);
            }
            Instant now = clock.instant();
            long nextVersion = existing.getVersion() + 1;
            if (!repository.revokeGrant(existing.getId(), existing.getVersion(), nextVersion,
                    actorId, reason, local(now))) {
                throw failure(OperatorAuthorizationFailure.GRANT_CONFLICT);
            }
            existing.setStatus(OperatorGrantStatus.REVOKED.name());
            existing.setVersion(nextVersion);
            existing.setRevokedAt(local(now));
            existing.setRevokedBy(actorId);
            existing.setRevocationReason(reason);
            appendAudit(existing, "REVOKE", OperatorGrantStatus.ACTIVE.name(), actorId, reason, now);
            long authorizationVersion = repository.bumpAuthorizationVersion(existing.getOperatorId());
            return new OperatorGrantResult(existing.getId(), existing.getStatus(), existing.getVersion(),
                    authorizationVersion);
        }));
    }

    private ValidGrant validate(GrantOperatorCommand command) {
        if (command.operatorId() <= 0 || command.expectedVersion() < 0) {
            throw new IllegalArgumentException("operatorId must be positive and expectedVersion non-negative");
        }
        OperatorGrant.validateScope(command.scopeType(), command.schoolId(), command.campusId());
        Set<OperatorAction> actions = Set.copyOf(Objects.requireNonNull(command.actions(), "actions"));
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("actions must not be empty");
        }
        Instant validFrom = Objects.requireNonNull(command.validFrom(), "validFrom");
        Instant expiresAt = Objects.requireNonNull(command.expiresAt(), "expiresAt");
        if (!expiresAt.isAfter(validFrom) || !clock.instant().isBefore(expiresAt)) {
            throw new IllegalArgumentException("grant expiry must be after validFrom and server time");
        }
        List<String> sortedActions = actions.stream().map(Enum::name).sorted().toList();
        return new ValidGrant(command.operatorId(), command.scopeType(), command.schoolId(), command.campusId(),
                actionsJson(sortedActions), validFrom, expiresAt, command.expectedVersion(),
                requireReason(command.reason()));
    }

    private void validateDirectoryScope(OperatorScopeType type, Long schoolId, Long campusId) {
        if (type == OperatorScopeType.PLATFORM) {
            return;
        }
        if (type == OperatorScopeType.CAMPUS) {
            campusScopeApi.assertCampusBelongsToSchool(schoolId, campusId);
            return;
        }
        SchoolDO school = schoolRepository.findSchool(schoolId);
        if (school == null || !CampusStatus.ENABLED.equals(school.getStatus())) {
            throw failure(OperatorAuthorizationFailure.SCOPE_DENIED);
        }
    }

    private void assertGrantManager(long actorId) {
        if (actorId <= 0) {
            throw failure(OperatorAuthorizationFailure.OPERATOR_DISABLED);
        }
        operatorAccountPort.assertActive(actorId);
        if (!operatorAccountPort.hasPermission(actorId, OperatorAction.OPERATOR_GRANT_MANAGE.permission())) {
            throw failure(OperatorAuthorizationFailure.ACTION_DENIED);
        }
    }

    private void appendAudit(OperatorGrantDO grant, String action, String fromStatus,
                             long actorId, String reason, Instant occurredAt) {
        OperatorGrantAuditDO audit = new OperatorGrantAuditDO();
        audit.setGrantId(grant.getId());
        audit.setAction(action);
        audit.setFromStatus(fromStatus);
        audit.setToStatus(grant.getStatus());
        audit.setOperatorId(actorId);
        audit.setReason(reason);
        audit.setGrantVersion(grant.getVersion());
        audit.setSnapshotJson(snapshotJson(grant));
        audit.setOccurredAt(local(occurredAt));
        repository.appendAudit(audit);
    }

    private String snapshotJson(OperatorGrantDO grant) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("operatorId", grant.getOperatorId());
        snapshot.put("scopeType", grant.getScopeType());
        snapshot.put("schoolId", grant.getSchoolId());
        snapshot.put("campusId", grant.getCampusId());
        snapshot.put("actions", readActionNames(grant.getActionsJson()));
        snapshot.put("status", grant.getStatus());
        snapshot.put("validFrom", grant.getValidFrom().toString());
        snapshot.put("expiresAt", grant.getExpiresAt().toString());
        snapshot.put("version", grant.getVersion());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("grant audit snapshot cannot be encoded", exception);
        }
    }

    private List<String> readActionNames(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("grant actions cannot be decoded", exception);
        }
    }

    private String actionsJson(List<String> actions) {
        try {
            return objectMapper.writeValueAsString(actions);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("grant actions cannot be encoded", exception);
        }
    }

    private static String requireReason(String value) {
        String reason = Objects.requireNonNull(value, "reason").trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new IllegalArgumentException("reason must contain 1..500 characters");
        }
        return reason;
    }

    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static OperatorAuthorizationException failure(OperatorAuthorizationFailure failure) {
        return new OperatorAuthorizationException(failure);
    }

    private record ValidGrant(long operatorId, OperatorScopeType scopeType, Long schoolId, Long campusId,
                              String actionsJson, Instant validFrom, Instant expiresAt,
                              long expectedVersion, String reason) {
    }
}
