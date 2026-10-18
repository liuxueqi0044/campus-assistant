package cn.iocoder.yudao.module.campus.identity.application.authorization;

import cn.iocoder.yudao.module.campus.identity.api.AuthorizedSchoolScope;
import cn.iocoder.yudao.module.campus.identity.api.CampusScopeApi;
import cn.iocoder.yudao.module.campus.identity.api.OperatorAction;
import cn.iocoder.yudao.module.campus.identity.api.OperatorAuthorizationApi;
import cn.iocoder.yudao.module.campus.identity.api.RequestedSchoolScope;
import cn.iocoder.yudao.module.campus.identity.application.MembershipVerificationException;
import cn.iocoder.yudao.module.campus.identity.application.MembershipVerificationFailure;
import cn.iocoder.yudao.module.campus.identity.application.port.OperatorAccountPort;
import cn.iocoder.yudao.module.campus.identity.application.port.ReviewAuthorizationPort;
import cn.iocoder.yudao.module.campus.identity.domain.authorization.OperatorGrant;
import cn.iocoder.yudao.module.campus.identity.domain.authorization.OperatorGrantStatus;
import cn.iocoder.yudao.module.campus.identity.domain.authorization.OperatorScopeType;
import cn.iocoder.yudao.module.campus.identity.infrastructure.OperatorGrantRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.OperatorGrantDO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OperatorAuthorizationService implements OperatorAuthorizationApi, ReviewAuthorizationPort {

    private final OperatorGrantRepository repository;
    private final OperatorAccountPort operatorAccountPort;
    private final CampusScopeApi campusScopeApi;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public AuthorizedSchoolScope authorize(long operatorId, OperatorAction action,
                                           RequestedSchoolScope requestedScope) {
        if (operatorId <= 0) {
            throw failure(OperatorAuthorizationFailure.OPERATOR_DISABLED);
        }
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(requestedScope, "requestedScope");
        operatorAccountPort.assertActive(operatorId);
        if (!operatorAccountPort.hasPermission(operatorId, action.permission())) {
            throw failure(OperatorAuthorizationFailure.ACTION_DENIED);
        }
        if (requestedScope.campusId() != null) {
            campusScopeApi.assertCampusBelongsToSchool(requestedScope.schoolId(), requestedScope.campusId());
        }

        List<OperatorGrant> scopeMatches = repository.findActiveGrants(operatorId).stream()
                .map(this::toDomain)
                .filter(grant -> grant.covers(requestedScope))
                .toList();
        if (scopeMatches.isEmpty()) {
            throw failure(OperatorAuthorizationFailure.SCOPE_DENIED);
        }
        List<OperatorGrant> current = scopeMatches.stream().filter(grant -> grant.isCurrent(clock)).toList();
        if (current.isEmpty()) {
            throw failure(OperatorAuthorizationFailure.GRANT_EXPIRED);
        }
        OperatorGrant selected = current.stream()
                .filter(grant -> grant.actions().contains(action))
                .max(Comparator.comparingInt(OperatorGrant::specificity)
                        .thenComparing(OperatorGrant::expiresAt))
                .orElseThrow(() -> failure(OperatorAuthorizationFailure.ACTION_DENIED));
        long authorizationVersion = repository.authorizationVersion(operatorId);
        if (authorizationVersion <= 0) {
            throw failure(OperatorAuthorizationFailure.SCOPE_DENIED);
        }
        return new AuthorizedSchoolScope(operatorId, action, requestedScope.schoolId(), requestedScope.campusId(),
                selected.id(), selected.scopeType().name(), selected.version(), authorizationVersion,
                OffsetDateTime.ofInstant(selected.expiresAt(), ZoneOffset.UTC));
    }

    @Override
    public void assertAllowed(long operatorId, long schoolId, long campusId, String action) {
        try {
            authorize(operatorId, OperatorAction.reviewAction(action),
                    RequestedSchoolScope.campus(schoolId, campusId));
        } catch (OperatorAuthorizationException | IllegalArgumentException denied) {
            throw new MembershipVerificationException(
                    MembershipVerificationFailure.VERIFICATION_REVIEW_FORBIDDEN);
        }
    }

    private OperatorGrant toDomain(OperatorGrantDO row) {
        final OperatorScopeType scopeType;
        try {
            scopeType = OperatorScopeType.valueOf(row.getScopeType());
        } catch (RuntimeException exception) {
            throw failure(OperatorAuthorizationFailure.SCOPE_DENIED);
        }
        final OperatorGrantStatus status;
        try {
            status = OperatorGrantStatus.valueOf(row.getStatus());
        } catch (RuntimeException exception) {
            throw failure(OperatorAuthorizationFailure.SCOPE_DENIED);
        }
        try {
            return new OperatorGrant(row.getId(), row.getOperatorId(), scopeType,
                    row.getSchoolId(), row.getCampusId(), readActions(row.getActionsJson()), status,
                    row.getValidFrom().toInstant(ZoneOffset.UTC),
                    row.getExpiresAt().toInstant(ZoneOffset.UTC), row.getVersion());
        } catch (OperatorAuthorizationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(OperatorAuthorizationFailure.SCOPE_DENIED);
        }
    }

    private Set<OperatorAction> readActions(String json) {
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<>() { });
            Set<OperatorAction> actions = new LinkedHashSet<>();
            for (String value : values) {
                if (!actions.add(OperatorAction.storedAction(value))) {
                    throw failure(OperatorAuthorizationFailure.ACTION_DENIED);
                }
            }
            if (actions.isEmpty()) {
                throw failure(OperatorAuthorizationFailure.ACTION_DENIED);
            }
            return actions;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw failure(OperatorAuthorizationFailure.ACTION_DENIED);
        }
    }

    private static OperatorAuthorizationException failure(OperatorAuthorizationFailure failure) {
        return new OperatorAuthorizationException(failure);
    }
}
