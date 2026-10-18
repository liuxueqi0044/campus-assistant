package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;
import cn.iocoder.yudao.module.campus.common.idempotency.CommandExecution;
import cn.iocoder.yudao.module.campus.common.idempotency.IdempotentCommand;
import cn.iocoder.yudao.module.campus.common.idempotency.IdempotentCommandExecutor;
import cn.iocoder.yudao.module.campus.common.outbox.OutboxPublisher;
import cn.iocoder.yudao.module.campus.identity.api.CampusScopeApi;
import cn.iocoder.yudao.module.campus.identity.application.port.AccountStatusPort;
import cn.iocoder.yudao.module.campus.identity.application.port.ReviewAuthorizationPort;
import cn.iocoder.yudao.module.campus.identity.application.port.VerificationEvidencePort;
import cn.iocoder.yudao.module.campus.identity.application.port.VerificationProof;
import cn.iocoder.yudao.module.campus.identity.application.verification.ReviewDecision;
import cn.iocoder.yudao.module.campus.identity.application.verification.ReviewVerificationCommand;
import cn.iocoder.yudao.module.campus.identity.application.verification.RevokeVerificationCommand;
import cn.iocoder.yudao.module.campus.identity.application.verification.SubmitVerificationCommand;
import cn.iocoder.yudao.module.campus.identity.application.verification.VerificationApplicationResult;
import cn.iocoder.yudao.module.campus.identity.application.verification.VerificationReviewResult;
import cn.iocoder.yudao.module.campus.identity.domain.verification.VerificationAction;
import cn.iocoder.yudao.module.campus.identity.domain.verification.VerificationAttempt;
import cn.iocoder.yudao.module.campus.identity.domain.verification.VerificationStatus;
import cn.iocoder.yudao.module.campus.identity.infrastructure.MembershipVerificationRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.MembershipDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.VerificationDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.VerificationReviewDO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class VerificationApplicationService {

    static final String EVENT_TYPE = "identity.membership_changed.v1";
    private static final String SUBMIT_ACTION = "identity.verification.submit";
    private static final String REVIEW_ACTION = "identity.verification.review";
    private static final String REVOKE_ACTION = "identity.verification.revoke";

    private final MembershipVerificationRepository repository;
    private final CampusScopeApi campusScopeApi;
    private final VerificationEvidencePort evidencePort;
    private final ReviewAuthorizationPort authorizationPort;
    private final AccountStatusPort accountStatusPort;
    private final IdempotentCommandExecutor idempotency;
    private final OutboxPublisher outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final long platformTenantId;

    public VerificationApplicationService(MembershipVerificationRepository repository,
                                          CampusScopeApi campusScopeApi,
                                          VerificationEvidencePort evidencePort,
                                          ReviewAuthorizationPort authorizationPort,
                                          AccountStatusPort accountStatusPort,
                                          IdempotentCommandExecutor idempotency,
                                          OutboxPublisher outbox,
                                          ObjectMapper objectMapper,
                                          Clock clock,
                                          @Value("${campus.platform-tenant-id:1}") long platformTenantId) {
        this.repository = Objects.requireNonNull(repository);
        this.campusScopeApi = Objects.requireNonNull(campusScopeApi);
        this.evidencePort = Objects.requireNonNull(evidencePort);
        this.authorizationPort = Objects.requireNonNull(authorizationPort);
        this.accountStatusPort = Objects.requireNonNull(accountStatusPort);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.outbox = Objects.requireNonNull(outbox);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
        if (platformTenantId <= 0) {
            throw new IllegalArgumentException("platformTenantId must be positive");
        }
        this.platformTenantId = platformTenantId;
    }

    public VerificationApplicationResult submit(long userId, SubmitVerificationCommand command) {
        requirePositive(userId, "userId");
        SubmitInput input = validate(command);
        accountStatusPort.assertActive(userId);
        campusScopeApi.assertCampusBelongsToSchool(input.schoolId(), input.campusId());
        IdempotentCommand durable = new IdempotentCommand(platformTenantId, userId, SUBMIT_ACTION,
                input.idempotencyKey(), input.canonicalRequest(), null);
        try {
            return idempotency.execute(durable, VerificationApplicationResult.class, () -> {
                VerificationProof proof = evidencePort.verify(userId, input.schoolId(), input.campusId(),
                        input.method(), input.evidenceRefs());
                requireUsableProof(proof, true);
                VerificationDO row = new VerificationDO();
                row.setUserId(userId);
                row.setSchoolId(input.schoolId());
                row.setCampusId(input.campusId());
                row.setMethod(input.method());
                row.setEvidenceRefsJson(writeEvidenceRefs(input.evidenceRefs()));
                row.setProofReference(proof.reference());
                row.setStatus(VerificationStatus.PENDING.name());
                row.setVersion(1L);
                row.setSubmittedAt(nowLocal());
                repository.insertVerification(row);
                VerificationApplicationResult result = new VerificationApplicationResult(
                        String.valueOf(row.getId()), row.getStatus(), row.getVersion());
                return new CommandExecution<>(result, result.applicationId(), result.version());
            }, ignored -> {
                accountStatusPort.assertActive(userId);
                campusScopeApi.assertCampusBelongsToSchool(input.schoolId(), input.campusId());
            }).result();
        } catch (DuplicateKeyException duplicatePending) {
            throw failure(MembershipVerificationFailure.VERIFICATION_CONFLICT);
        }
    }

    public VerificationReviewResult review(long operatorId, ReviewVerificationCommand command) {
        Objects.requireNonNull(command, "command");
        VerificationAction action = Objects.requireNonNull(command.decision(), "decision") == ReviewDecision.APPROVE
                ? VerificationAction.APPROVE : VerificationAction.REJECT;
        return changeStatus(operatorId, command.verificationId(), action, command.reason(),
                command.expectedVersion(), command.idempotencyKey());
    }

    public VerificationReviewResult revoke(long operatorId, RevokeVerificationCommand command) {
        Objects.requireNonNull(command, "command");
        return changeStatus(operatorId, command.verificationId(), VerificationAction.REVOKE, command.reason(),
                command.expectedVersion(), command.idempotencyKey());
    }

    private VerificationReviewResult changeStatus(long operatorId, long verificationId, VerificationAction action,
                                                  String rawReason, long expectedVersion, String idempotencyKey) {
        requirePositive(operatorId, "operatorId");
        requirePositive(verificationId, "verificationId");
        requirePositive(expectedVersion, "expectedVersion");
        String reason = requireText(rawReason, "reason", 500);
        String key = requireText(idempotencyKey, "idempotencyKey", IdempotentCommand.KEY_MAX_LENGTH);
        VerificationDO probe = requiredVerification(repository.findVerification(verificationId));
        String authorizationAction = action == VerificationAction.REVOKE
                ? "MEMBERSHIP_REVOKE" : "VERIFICATION_REVIEW";
        authorizationPort.assertAllowed(operatorId, probe.getSchoolId(), authorizationAction);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("verificationId", verificationId);
        request.put("action", action.name());
        request.put("reason", reason);
        request.put("expectedVersion", expectedVersion);
        IdempotentCommand durable = new IdempotentCommand(platformTenantId, operatorId,
                action == VerificationAction.REVOKE ? REVOKE_ACTION : REVIEW_ACTION, key, request, null);
        return idempotency.execute(durable, VerificationReviewResult.class,
                () -> executeStatusChange(operatorId, verificationId, action, reason, expectedVersion),
                ignored -> authorizationPort.assertAllowed(operatorId, probe.getSchoolId(), authorizationAction))
                .result();
    }

    private CommandExecution<VerificationReviewResult> executeStatusChange(long operatorId, long verificationId,
                                                                            VerificationAction action, String reason,
                                                                            long expectedVersion) {
        VerificationDO row = requiredVerification(repository.lockVerification(verificationId));
        MembershipDO membership = repository.lockMembership(row.getUserId(), row.getSchoolId());
        VerificationStatus target = target(action);
        if (row.getStatus().equals(target.name()) && row.getVersion() == expectedVersion + 1) {
            VerificationReviewResult replay = existingResult(row, membership, action);
            return execution(replay);
        }
        VerificationAttempt current = new VerificationAttempt(row.getId(), row.getUserId(), row.getSchoolId(),
                row.getCampusId(), VerificationStatus.valueOf(row.getStatus()), row.getVersion());
        VerificationAttempt changed;
        try {
            changed = current.transition(action, expectedVersion);
        } catch (IllegalStateException conflict) {
            throw failure(MembershipVerificationFailure.VERIFICATION_CONFLICT);
        }
        Instant now = clock.instant();
        VerificationProof proof = null;
        if (action == VerificationAction.APPROVE) {
            accountStatusPort.assertActive(row.getUserId());
            proof = evidencePort.verify(row.getUserId(), row.getSchoolId(), row.getCampusId(), row.getMethod(),
                    readEvidenceRefs(row.getEvidenceRefsJson()));
            requireUsableProof(proof, true);
        }
        LocalDateTime validFrom = action == VerificationAction.REJECT ? null
                : action == VerificationAction.REVOKE ? row.getValidFrom() : local(proof.validFrom());
        LocalDateTime expiresAt = action == VerificationAction.REJECT ? null
                : action == VerificationAction.REVOKE ? row.getExpiresAt() : local(proof.expiresAt());
        boolean transitioned = repository.transitionVerification(row.getId(), row.getStatus(), target.name(),
                expectedVersion, operatorId, reason, local(now), validFrom, expiresAt);
        if (!transitioned) {
            throw failure(MembershipVerificationFailure.VERIFICATION_CONFLICT);
        }
        appendReview(row, changed, action, operatorId, reason, now);
        MembershipDO changedMembership = membership;
        if (action == VerificationAction.APPROVE) {
            changedMembership = grantMembership(row, membership, operatorId, proof);
            publishMembershipChanged(changedMembership, now);
        } else if (action == VerificationAction.REVOKE) {
            changedMembership = revokeMembership(row, membership, operatorId, reason, now);
            publishMembershipChanged(changedMembership, now);
        }
        VerificationReviewResult result = result(row.getId(), target, changed.version(), changedMembership,
                action != VerificationAction.REJECT);
        return execution(result);
    }

    private MembershipDO grantMembership(VerificationDO verification, MembershipDO existing, long operatorId,
                                         VerificationProof proof) {
        if (existing == null) {
            MembershipDO created = new MembershipDO();
            created.setUserId(verification.getUserId());
            created.setSchoolId(verification.getSchoolId());
            created.setCampusId(verification.getCampusId());
            created.setSourceVerificationId(verification.getId());
            created.setStatus("ACTIVE");
            created.setEffectiveFrom(local(proof.validFrom()));
            created.setExpiresAt(local(proof.expiresAt()));
            created.setScopeVersion(1L);
            repository.insertMembership(created);
            return created;
        }
        assertMembershipOwner(existing, verification);
        long expectedScopeVersion = existing.getScopeVersion();
        existing.setCampusId(verification.getCampusId());
        existing.setSourceVerificationId(verification.getId());
        existing.setStatus("ACTIVE");
        existing.setEffectiveFrom(local(proof.validFrom()));
        existing.setExpiresAt(local(proof.expiresAt()));
        existing.setScopeVersion(expectedScopeVersion + 1);
        existing.setRevokedAt(null);
        existing.setRevokedBy(null);
        existing.setRevocationReason(null);
        if (!repository.grantMembership(existing, expectedScopeVersion, operatorId)) {
            throw failure(MembershipVerificationFailure.VERIFICATION_CONFLICT);
        }
        return existing;
    }

    private MembershipDO revokeMembership(VerificationDO verification, MembershipDO membership, long operatorId,
                                          String reason, Instant now) {
        if (membership == null) {
            throw failure(MembershipVerificationFailure.MEMBERSHIP_REQUIRED);
        }
        assertMembershipSource(membership, verification);
        long expectedScopeVersion = membership.getScopeVersion();
        membership.setStatus("REVOKED");
        membership.setScopeVersion(expectedScopeVersion + 1);
        membership.setRevokedAt(local(now));
        membership.setRevokedBy(operatorId);
        membership.setRevocationReason(reason);
        if (!repository.revokeMembership(membership, verification.getId(), expectedScopeVersion, operatorId,
                reason, local(now))) {
            throw failure(MembershipVerificationFailure.VERIFICATION_CONFLICT);
        }
        return membership;
    }

    private void appendReview(VerificationDO before, VerificationAttempt changed, VerificationAction action,
                              long operatorId, String reason, Instant now) {
        VerificationReviewDO review = new VerificationReviewDO();
        review.setVerificationId(before.getId());
        review.setFromStatus(before.getStatus());
        review.setToStatus(changed.status().name());
        review.setAction(action.name());
        review.setOperatorId(operatorId);
        review.setReason(reason);
        review.setVerificationVersion(changed.version());
        review.setOccurredAt(local(now));
        repository.appendReview(review);
    }

    private void publishMembershipChanged(MembershipDO membership, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", membership.getUserId());
        payload.put("schoolId", membership.getSchoolId());
        payload.put("campusId", membership.getCampusId());
        payload.put("membershipId", membership.getId());
        payload.put("status", membership.getStatus());
        payload.put("scopeVersion", membership.getScopeVersion());
        CampusEventEnvelope event = CampusEventEnvelope.builder()
                .eventId("membership-" + membership.getId() + "-v" + membership.getScopeVersion())
                .type(EVENT_TYPE)
                .version(1)
                .aggregateId(String.valueOf(membership.getId()))
                .aggregateVersion(membership.getScopeVersion())
                .schoolId(String.valueOf(membership.getSchoolId()))
                .occurredAt(OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .payload(payload)
                .build();
        outbox.publish(event);
    }

    private VerificationReviewResult existingResult(VerificationDO row, MembershipDO membership,
                                                    VerificationAction action) {
        if (action == VerificationAction.REJECT) {
            return result(row.getId(), VerificationStatus.REJECTED, row.getVersion(), null, false);
        }
        if (membership == null) {
            throw failure(MembershipVerificationFailure.VERIFICATION_CONFLICT);
        }
        assertMembershipSource(membership, row);
        String expectedMembershipStatus = action == VerificationAction.REVOKE ? "REVOKED" : "ACTIVE";
        if (!expectedMembershipStatus.equals(membership.getStatus())) {
            throw failure(MembershipVerificationFailure.VERIFICATION_CONFLICT);
        }
        return result(row.getId(), VerificationStatus.valueOf(row.getStatus()), row.getVersion(), membership, true);
    }

    private static void assertMembershipOwner(MembershipDO membership, VerificationDO verification) {
        if (!Objects.equals(membership.getUserId(), verification.getUserId())
                || !Objects.equals(membership.getSchoolId(), verification.getSchoolId())) {
            throw failure(MembershipVerificationFailure.VERIFICATION_CONFLICT);
        }
    }

    private static void assertMembershipSource(MembershipDO membership, VerificationDO verification) {
        assertMembershipOwner(membership, verification);
        if (!Objects.equals(membership.getCampusId(), verification.getCampusId())
                || !Objects.equals(membership.getSourceVerificationId(), verification.getId())) {
            throw failure(MembershipVerificationFailure.VERIFICATION_CONFLICT);
        }
    }

    private void requireUsableProof(VerificationProof proof, boolean requireCurrentlyValid) {
        if (proof == null) {
            throw failure(MembershipVerificationFailure.MEDIA_NOT_READY);
        }
        if (requireCurrentlyValid && !clock.instant().isBefore(proof.expiresAt())) {
            throw failure(MembershipVerificationFailure.MEDIA_NOT_READY);
        }
    }

    private static VerificationReviewResult result(long verificationId, VerificationStatus status, long version,
                                                   MembershipDO membership, boolean includeMembership) {
        return new VerificationReviewResult(String.valueOf(verificationId), status.name(), version,
                includeMembership ? String.valueOf(membership.getId()) : null,
                includeMembership ? membership.getScopeVersion() : null);
    }

    private static CommandExecution<VerificationReviewResult> execution(VerificationReviewResult result) {
        return new CommandExecution<>(result, result.applicationId(), result.version());
    }

    private SubmitInput validate(SubmitVerificationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.schoolId(), "schoolId");
        requirePositive(command.campusId(), "campusId");
        String method = requireText(command.method(), "method", 32).toUpperCase(java.util.Locale.ROOT);
        String key = requireText(command.idempotencyKey(), "idempotencyKey", IdempotentCommand.KEY_MAX_LENGTH);
        List<String> refs = Objects.requireNonNull(command.evidenceRefs(), "evidenceRefs").stream()
                .map(ref -> requireOpaqueReference(ref, "evidenceRef"))
                .toList();
        if (refs.isEmpty() || refs.size() > 10 || refs.stream().distinct().count() != refs.size()) {
            throw new IllegalArgumentException("evidenceRefs must contain 1..10 unique opaque identifiers");
        }
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("schoolId", command.schoolId());
        canonical.put("campusId", command.campusId());
        canonical.put("method", method);
        canonical.put("evidenceRefs", refs);
        return new SubmitInput(command.schoolId(), command.campusId(), method, refs, key, canonical);
    }

    private String writeEvidenceRefs(List<String> evidenceRefs) {
        try {
            return objectMapper.writeValueAsString(evidenceRefs);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("evidence references cannot be encoded", exception);
        }
    }

    private List<String> readEvidenceRefs(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("persisted evidence references cannot be decoded", exception);
        }
    }

    private static VerificationDO requiredVerification(VerificationDO row) {
        if (row == null) {
            throw failure(MembershipVerificationFailure.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    private Instant now() {
        return clock.instant();
    }

    private LocalDateTime nowLocal() {
        return local(now());
    }

    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static VerificationStatus target(VerificationAction action) {
        return switch (action) {
            case APPROVE -> VerificationStatus.APPROVED;
            case REJECT -> VerificationStatus.REJECTED;
            case REVOKE -> VerificationStatus.REVOKED;
        };
    }

    private static String requireOpaqueReference(String value, String name) {
        String result = requireText(value, name, 128);
        if (result.contains("://")) {
            throw new IllegalArgumentException(name + " must be an opaque identifier, not a URL");
        }
        return result;
    }

    private static String requireText(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name);
        String result = value.trim();
        if (result.isEmpty() || result.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1.." + maxLength + " characters");
        }
        return result;
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static MembershipVerificationException failure(MembershipVerificationFailure failure) {
        return new MembershipVerificationException(failure);
    }

    private record SubmitInput(long schoolId, long campusId, String method, List<String> evidenceRefs,
                               String idempotencyKey, Map<String, Object> canonicalRequest) {
    }
}
