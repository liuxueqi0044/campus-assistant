package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.module.campus.identity.api.OperatorAction;
import cn.iocoder.yudao.module.campus.identity.api.RequestedSchoolScope;
import cn.iocoder.yudao.module.campus.identity.application.authorization.OperatorAuthorizationService;
import cn.iocoder.yudao.module.campus.identity.application.port.OperatorAccountPort;
import cn.iocoder.yudao.module.campus.identity.controller.admin.vo.CampusAdminContextResponse;
import cn.iocoder.yudao.module.campus.identity.controller.admin.vo.VerificationDetailResponse;
import cn.iocoder.yudao.module.campus.identity.controller.admin.vo.VerificationQueueResponse;
import cn.iocoder.yudao.module.campus.identity.infrastructure.MembershipVerificationRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.OperatorGrantRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.SchoolRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.CampusDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.OperatorGrantDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.SchoolDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.VerificationDO;
import cn.iocoder.yudao.module.campus.identity.domain.authorization.OperatorScopeType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CampusAdminQueryService {

    private final OperatorAccountPort operatorAccountPort;
    private final OperatorGrantRepository grantRepository;
    private final OperatorAuthorizationService authorization;
    private final SchoolRepository schoolRepository;
    private final MembershipVerificationRepository verificationRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CampusAdminContextResponse context(long operatorId) {
        operatorAccountPort.assertActive(operatorId);
        List<String> permissions = java.util.Arrays.stream(OperatorAction.values())
                .filter(action -> operatorAccountPort.hasPermission(operatorId, action.permission()))
                .map(OperatorAction::permission).sorted().toList();
        List<OperatorGrantDO> grants = grantRepository.findActiveGrants(operatorId).stream()
                .filter(grant -> isCurrent(grant)).toList();
        Map<Long, ScopeAccumulator> schools = new LinkedHashMap<>();
        for (SchoolDO school : enabledSchools()) {
            ScopeAccumulator accumulator = new ScopeAccumulator(school.getId(), school.getName());
            for (CampusDO campus : schoolRepository.findEnabledCampuses(school.getId())) {
                accumulator.campuses.put(campus.getId(), new ScopeAccumulator.Campus(campus.getId(), campus.getName()));
            }
            schools.put(school.getId(), accumulator);
        }
        for (OperatorGrantDO grant : grants) {
            Set<String> actions = readActions(grant.getActionsJson()).stream()
                    .filter(value -> {
                        try { return operatorAccountPort.hasPermission(operatorId, OperatorAction.valueOf(value).permission()); }
                        catch (IllegalArgumentException ignored) { return false; }
                    }).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if ("PLATFORM".equals(grant.getScopeType())) {
                schools.values().forEach(scope -> scope.add(actions));
            } else if ("SCHOOL".equals(grant.getScopeType()) && schools.containsKey(grant.getSchoolId())) {
                schools.get(grant.getSchoolId()).add(actions);
            } else if ("CAMPUS".equals(grant.getScopeType()) && schools.containsKey(grant.getSchoolId())) {
                ScopeAccumulator scope = schools.get(grant.getSchoolId());
                ScopeAccumulator.Campus campus = scope.campuses.get(grant.getCampusId());
                if (campus != null) campus.actions.addAll(actions);
            }
        }
        List<CampusAdminContextResponse.SchoolScope> result = schools.values().stream()
                .filter(scope -> !scope.actions.isEmpty() || scope.campuses.values().stream().anyMatch(c -> !c.actions.isEmpty()))
                .map(scope -> scope.response()).toList();
        Set<String> menus = new LinkedHashSet<>();
        if (permissions.contains(OperatorAction.VERIFICATION_REVIEW.permission())) menus.add("campus.verifications");
        if (permissions.contains(OperatorAction.MEMBERSHIP_REVOKE.permission())) menus.add("campus.memberships");
        return new CampusAdminContextResponse(String.valueOf(operatorId),
                Math.max(1L, grantRepository.authorizationVersion(operatorId)),
                permissions, result, menus.stream().sorted().toList());
    }

    public VerificationQueueResponse queue(long operatorId, Long schoolId, Long campusId, String status,
                                            int page, int pageSize) {
        operatorAccountPort.assertActive(operatorId);
        requireReviewPermission(operatorId);
        String normalizedStatus = normalizeStatus(status);
        int boundedPage = page <= 0 ? 1 : page;
        int boundedSize = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        List<VerificationDO> authorized = verificationRepository.findVerificationsByStatus(normalizedStatus).stream()
                .filter(row -> schoolId == null || schoolId.equals(row.getSchoolId()))
                .filter(row -> campusId == null || campusId.equals(row.getCampusId()))
                .filter(row -> allowed(operatorId, row.getSchoolId(), row.getCampusId())).toList();
        int from = Math.min((boundedPage - 1) * boundedSize, authorized.size());
        int to = Math.min(from + boundedSize, authorized.size());
        List<VerificationQueueResponse.Item> items = authorized.subList(from, to).stream().map(this::queueItem).toList();
        return new VerificationQueueResponse(items, authorized.size(), boundedPage, boundedSize);
    }

    public VerificationDetailResponse detail(long operatorId, long applicationId) {
        operatorAccountPort.assertActive(operatorId);
        requireReviewPermission(operatorId);
        VerificationDO row = verificationRepository.findVerification(applicationId);
        if (row == null || !allowed(operatorId, row.getSchoolId(), row.getCampusId())) {
            throw new MembershipVerificationException(MembershipVerificationFailure.RESOURCE_NOT_FOUND);
        }
        List<VerificationDetailResponse.Evidence> evidence = readEvidence(row.getEvidenceRefsJson()).stream()
                .map(VerificationDetailResponse.Evidence::new).toList();
        return new VerificationDetailResponse(String.valueOf(row.getId()), String.valueOf(row.getSchoolId()),
                String.valueOf(row.getCampusId()), row.getMethod(), row.getStatus(), row.getVersion(),
                timestamp(row.getSubmittedAt()), evidence);
    }

    private boolean allowed(long operatorId, long schoolId, long campusId) {
        try {
            authorization.authorize(operatorId, OperatorAction.VERIFICATION_REVIEW,
                    RequestedSchoolScope.campus(schoolId, campusId));
            return true;
        } catch (cn.iocoder.yudao.module.campus.identity.application.authorization.OperatorAuthorizationException
                 | IllegalArgumentException denied) {
            return false;
        }
    }

    private void requireReviewPermission(long operatorId) {
        if (!operatorAccountPort.hasPermission(operatorId, OperatorAction.VERIFICATION_REVIEW.permission())) {
            throw new cn.iocoder.yudao.module.campus.identity.application.authorization.OperatorAuthorizationException(
                    cn.iocoder.yudao.module.campus.identity.application.authorization.OperatorAuthorizationFailure.ACTION_DENIED);
        }
    }

    private List<SchoolDO> enabledSchools() {
        return schoolRepository.findEnabledSchoolsAfter(Integer.MIN_VALUE, Long.MIN_VALUE, null, 10000);
    }

    private boolean isCurrent(OperatorGrantDO grant) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now(clock);
        return grant.getValidFrom() != null && grant.getExpiresAt() != null
                && !now.isBefore(grant.getValidFrom()) && now.isBefore(grant.getExpiresAt());
    }

    private Set<String> readActions(String raw) {
        try { return new LinkedHashSet<>(objectMapper.readValue(raw, new TypeReference<List<String>>() { })); }
        catch (Exception exception) { return Set.of(); }
    }

    private String normalizeStatus(String status) {
        String value = status == null || status.isBlank() ? "PENDING" : status.toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("PENDING", "APPROVED", "REJECTED", "REVOKED").contains(value)) {
            throw new IllegalArgumentException("unsupported verification status");
        }
        return value;
    }

    private VerificationQueueResponse.Item queueItem(VerificationDO row) {
        return new VerificationQueueResponse.Item(String.valueOf(row.getId()), String.valueOf(row.getSchoolId()),
                String.valueOf(row.getCampusId()), row.getMethod(), row.getStatus(), row.getVersion(), timestamp(row.getSubmittedAt()));
    }

    private OffsetDateTime timestamp(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).atOffset(ZoneOffset.UTC);
    }

    private List<String> readEvidence(String raw) {
        try { return objectMapper.readValue(raw, new TypeReference<List<String>>() { }); }
        catch (Exception exception) { return List.of(); }
    }

    private static final class ScopeAccumulator {
        final long schoolId; final String name; final Set<String> actions = new LinkedHashSet<>();
        final Map<Long, Campus> campuses = new LinkedHashMap<>();
        ScopeAccumulator(long schoolId, String name) { this.schoolId = schoolId; this.name = name; }
        void add(Set<String> values) { actions.addAll(values); campuses.values().forEach(c -> c.actions.addAll(values)); }
        CampusAdminContextResponse.SchoolScope response() {
            return new CampusAdminContextResponse.SchoolScope(String.valueOf(schoolId), name,
                    actions.stream().sorted().toList(), campuses.values().stream()
                            .filter(c -> !c.actions.isEmpty()).map(c -> new CampusAdminContextResponse.CampusScope(
                                    String.valueOf(c.id), c.name, c.actions.stream().sorted().toList())).toList());
        }
        static final class Campus { final long id; final String name; final Set<String> actions = new LinkedHashSet<>(); Campus(long id, String name) { this.id=id; this.name=name; } }
    }
}
