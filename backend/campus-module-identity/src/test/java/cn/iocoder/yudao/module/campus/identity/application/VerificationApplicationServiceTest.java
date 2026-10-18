package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;
import cn.iocoder.yudao.module.campus.common.idempotency.IdempotencyKey;
import cn.iocoder.yudao.module.campus.common.idempotency.IdempotencyRecord;
import cn.iocoder.yudao.module.campus.common.idempotency.IdempotencyRepository;
import cn.iocoder.yudao.module.campus.common.idempotency.IdempotencyReservation;
import cn.iocoder.yudao.module.campus.common.idempotency.IdempotencyStatus;
import cn.iocoder.yudao.module.campus.common.idempotency.IdempotentCommandExecutor;
import cn.iocoder.yudao.module.campus.common.idempotency.RequestCanonicalizer;
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
import cn.iocoder.yudao.module.campus.identity.application.verification.VerificationReviewResult;
import cn.iocoder.yudao.module.campus.identity.infrastructure.MembershipVerificationRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.MembershipDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.VerificationDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.VerificationReviewDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerificationApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");
    private static final Instant EXPIRY = Instant.parse("2027-09-17T10:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MembershipVerificationRepository repository = mock(MembershipVerificationRepository.class);
    private final CampusScopeApi campusScopeApi = mock(CampusScopeApi.class);
    private final VerificationEvidencePort evidencePort = mock(VerificationEvidencePort.class);
    private final ReviewAuthorizationPort authorizationPort = mock(ReviewAuthorizationPort.class);
    private final AccountStatusPort accountStatusPort = mock(AccountStatusPort.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private final TestState state = new TestState();
    private VerificationApplicationService service;

    @BeforeEach
    void setUp() {
        wireRepository();
        when(evidencePort.verify(anyLong(), anyLong(), anyLong(), anyString(), any()))
                .thenReturn(new VerificationProof("proof-opaque", NOW.minusSeconds(60), EXPIRY));
        doAnswer(invocation -> {
            CampusEventEnvelope event = invocation.getArgument(0);
            state.events.add(event);
            if (state.failOutbox) {
                throw new IllegalStateException("forced outbox failure");
            }
            return null;
        }).when(outbox).publish(any());
        IdempotentCommandExecutor executor = new IdempotentCommandExecutor(
                new SnapshotTransactions(state), state.idempotency,
                new RequestCanonicalizer(objectMapper), objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
        service = new VerificationApplicationService(repository, campusScopeApi, evidencePort, authorizationPort,
                accountStatusPort, executor, outbox, objectMapper, Clock.fixed(NOW, ZoneOffset.UTC), 1L);
    }

    @Test
    void approvalCreatesMembershipReviewAndOneSanitizedEvent() {
        state.verification = pending(51, 101, 1, 11);

        VerificationReviewResult result = service.review(901,
                new ReviewVerificationCommand(51, ReviewDecision.APPROVE, "verified", 1, "approve-51"));

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.scopeVersion()).isEqualTo(1L);
        assertThat(state.verification.getStatus()).isEqualTo("APPROVED");
        assertThat(state.membership.getSourceVerificationId()).isEqualTo(51L);
        assertThat(state.membership.getStatus()).isEqualTo("ACTIVE");
        assertThat(state.reviews).singleElement().satisfies(review -> {
            assertThat(review.getAction()).isEqualTo("APPROVE");
            assertThat(review.getVerificationVersion()).isEqualTo(2L);
        });
        assertThat(state.events).singleElement().satisfies(event -> {
            assertThat(event.getType()).isEqualTo(VerificationApplicationService.EVENT_TYPE);
            assertThat(event.getPayload()).containsOnlyKeys(
                    "userId", "schoolId", "campusId", "membershipId", "status", "scopeVersion");
        });
    }

    @Test
    void rejectionLeavesExistingEffectiveMembershipUntouched() {
        state.verification = pending(52, 101, 1, 12);
        state.membership = activeMembership(700, 101, 1, 11, 40, 5);
        MembershipDO before = copy(state.membership);

        VerificationReviewResult result = service.review(901,
                new ReviewVerificationCommand(52, ReviewDecision.REJECT, "not enough proof", 1, "reject-52"));

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.membershipId()).isNull();
        assertThat(state.membership).usingRecursiveComparison().isEqualTo(before);
        assertThat(state.reviews).hasSize(1);
        assertThat(state.events).isEmpty();
    }

    @Test
    void reapprovalUpdatesTheUniqueMembershipAndIncrementsScopeVersion() {
        state.verification = pending(52, 101, 1, 12);
        state.membership = activeMembership(700, 101, 1, 11, 40, 5);

        VerificationReviewResult result = service.review(901,
                new ReviewVerificationCommand(52, ReviewDecision.APPROVE, "new campus verified", 1, "approve-52"));

        assertThat(result.scopeVersion()).isEqualTo(6L);
        assertThat(state.membership.getId()).isEqualTo(700L);
        assertThat(state.membership.getCampusId()).isEqualTo(12L);
        assertThat(state.membership.getSourceVerificationId()).isEqualTo(52L);
        assertThat(state.membership.getScopeVersion()).isEqualTo(6L);
        assertThat(state.events).singleElement().extracting(CampusEventEnvelope::getAggregateVersion)
                .isEqualTo(6L);
    }

    @Test
    void oldVerificationCannotRevokeMembershipCreatedByANewerAttemptAndAllWritesRollBack() {
        state.verification = approved(51, 101, 1, 11, 2);
        state.membership = activeMembership(700, 101, 1, 11, 52, 6);

        assertThatThrownBy(() -> service.revoke(901,
                new RevokeVerificationCommand(51, "old proof invalid", 2, "revoke-old")))
                .isInstanceOfSatisfying(MembershipVerificationException.class,
                        error -> assertThat(error.failure())
                                .isEqualTo(MembershipVerificationFailure.VERIFICATION_CONFLICT));

        assertThat(state.verification.getStatus()).isEqualTo("APPROVED");
        assertThat(state.verification.getVersion()).isEqualTo(2L);
        assertThat(state.membership.getStatus()).isEqualTo("ACTIVE");
        assertThat(state.membership.getSourceVerificationId()).isEqualTo(52L);
        assertThat(state.reviews).isEmpty();
        assertThat(state.events).isEmpty();
        assertThat(state.idempotency.records).isEmpty();
    }

    @Test
    void revokeInvalidatesTheCurrentMembershipAndIncrementsScopeVersion() {
        state.verification = approved(51, 101, 1, 11, 2);
        state.membership = activeMembership(700, 101, 1, 11, 51, 6);

        VerificationReviewResult result = service.revoke(901,
                new RevokeVerificationCommand(51, "proof withdrawn", 2, "revoke-current"));

        assertThat(result.status()).isEqualTo("REVOKED");
        assertThat(result.scopeVersion()).isEqualTo(7L);
        assertThat(state.verification.getStatus()).isEqualTo("REVOKED");
        assertThat(state.verification.getVersion()).isEqualTo(3L);
        assertThat(state.membership.getStatus()).isEqualTo("REVOKED");
        assertThat(state.membership.getScopeVersion()).isEqualTo(7L);
        assertThat(state.membership.getRevokedBy()).isEqualTo(901L);
        assertThat(state.reviews).singleElement().satisfies(review -> {
            assertThat(review.getFromStatus()).isEqualTo("APPROVED");
            assertThat(review.getToStatus()).isEqualTo("REVOKED");
            assertThat(review.getAction()).isEqualTo("REVOKE");
        });
        assertThat(state.events).singleElement().satisfies(event -> {
            assertThat(event.getPayload().get("status")).isEqualTo("REVOKED");
            assertThat(event.getAggregateVersion()).isEqualTo(7L);
        });
    }

    @Test
    void outboxFailureRollsBackDecisionMembershipReviewAndIdempotencyReservation() {
        state.verification = pending(51, 101, 1, 11);
        state.failOutbox = true;

        assertThatThrownBy(() -> service.review(901,
                new ReviewVerificationCommand(51, ReviewDecision.APPROVE, "verified", 1, "approve-fails")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced outbox failure");

        assertThat(state.verification.getStatus()).isEqualTo("PENDING");
        assertThat(state.verification.getVersion()).isEqualTo(1L);
        assertThat(state.membership).isNull();
        assertThat(state.reviews).isEmpty();
        assertThat(state.events).isEmpty();
        assertThat(state.idempotency.records).isEmpty();
    }

    @Test
    void sameIdempotencyKeyReplaysOriginalResultWithoutAnotherEventOrReview() {
        state.verification = pending(51, 101, 1, 11);
        ReviewVerificationCommand command = new ReviewVerificationCommand(
                51, ReviewDecision.APPROVE, "verified", 1, "approve-replay");

        VerificationReviewResult first = service.review(901, command);
        VerificationReviewResult second = service.review(901, command);

        assertThat(second).isEqualTo(first);
        assertThat(state.reviews).hasSize(1);
        assertThat(state.events).hasSize(1);
    }

    @Test
    void staleOpposingDecisionIsRejectedWithoutOverwritingTheWinner() {
        state.verification = approved(51, 101, 1, 11, 2);
        state.membership = activeMembership(700, 101, 1, 11, 51, 1);

        assertThatThrownBy(() -> service.review(902,
                new ReviewVerificationCommand(51, ReviewDecision.REJECT, "reject", 1, "late-reject")))
                .isInstanceOfSatisfying(MembershipVerificationException.class,
                        error -> assertThat(error.failure())
                                .isEqualTo(MembershipVerificationFailure.VERIFICATION_CONFLICT));

        assertThat(state.verification.getStatus()).isEqualTo("APPROVED");
        assertThat(state.membership.getStatus()).isEqualTo("ACTIVE");
        assertThat(state.reviews).isEmpty();
        assertThat(state.events).isEmpty();
    }

    @Test
    void submitRejectsRawUrlsBeforeAnyApplicationIsPersisted() {
        assertThatThrownBy(() -> service.submit(101,
                new SubmitVerificationCommand(1, 11, "STUDENT_CARD",
                        List.of("https://private.example/id-card"), "submit-url")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("opaque identifier");
        assertThat(state.verification).isNull();
        assertThat(state.idempotency.records).isEmpty();
    }

    private void wireRepository() {
        when(repository.findVerification(anyLong())).thenAnswer(invocation ->
                matches(state.verification, invocation.getArgument(0)) ? state.verification : null);
        when(repository.lockVerification(anyLong())).thenAnswer(invocation ->
                matches(state.verification, invocation.getArgument(0)) ? state.verification : null);
        when(repository.lockMembership(anyLong(), anyLong())).thenAnswer(invocation -> state.membership);
        doAnswer(invocation -> {
            VerificationDO inserted = invocation.getArgument(0);
            inserted.setId(51L);
            state.verification = inserted;
            return null;
        }).when(repository).insertVerification(any());
        when(repository.transitionVerification(anyLong(), anyString(), anyString(), anyLong(), anyLong(),
                anyString(), any(), any(), any())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            String expectedStatus = invocation.getArgument(1);
            String targetStatus = invocation.getArgument(2);
            long expectedVersion = invocation.getArgument(3);
            if (!matches(state.verification, id)
                    || !expectedStatus.equals(state.verification.getStatus())
                    || expectedVersion != state.verification.getVersion()) {
                return false;
            }
            VerificationDO updated = copy(state.verification);
            updated.setStatus(targetStatus);
            updated.setVersion(expectedVersion + 1);
            updated.setReviewedAt(invocation.getArgument(6));
            updated.setReviewedBy(invocation.getArgument(4));
            updated.setReviewReason(invocation.getArgument(5));
            updated.setValidFrom(invocation.getArgument(7));
            updated.setExpiresAt(invocation.getArgument(8));
            state.verification = updated;
            return true;
        });
        doAnswer(invocation -> {
            VerificationReviewDO review = invocation.getArgument(0);
            state.reviews.add(copy(review));
            return null;
        }).when(repository).appendReview(any());
        doAnswer(invocation -> {
            MembershipDO inserted = invocation.getArgument(0);
            inserted.setId(700L);
            state.membership = inserted;
            return null;
        }).when(repository).insertMembership(any());
        when(repository.grantMembership(any(), anyLong(), anyLong())).thenReturn(true);
        when(repository.revokeMembership(any(), anyLong(), anyLong(), anyLong(), anyString(), any()))
                .thenReturn(true);
    }

    private static boolean matches(VerificationDO verification, long id) {
        return verification != null && verification.getId() == id;
    }

    private static VerificationDO pending(long id, long userId, long schoolId, long campusId) {
        VerificationDO row = baseVerification(id, userId, schoolId, campusId);
        row.setStatus("PENDING");
        row.setVersion(1L);
        return row;
    }

    private static VerificationDO approved(long id, long userId, long schoolId, long campusId, long version) {
        VerificationDO row = baseVerification(id, userId, schoolId, campusId);
        row.setStatus("APPROVED");
        row.setVersion(version);
        row.setReviewedAt(local(NOW));
        row.setReviewedBy(901L);
        row.setReviewReason("approved");
        row.setValidFrom(local(NOW.minusSeconds(60)));
        row.setExpiresAt(local(EXPIRY));
        return row;
    }

    private static VerificationDO baseVerification(long id, long userId, long schoolId, long campusId) {
        VerificationDO row = new VerificationDO();
        row.setId(id);
        row.setUserId(userId);
        row.setSchoolId(schoolId);
        row.setCampusId(campusId);
        row.setMethod("STUDENT_CARD");
        row.setEvidenceRefsJson("[\"asset-a\"]");
        row.setProofReference("proof-opaque");
        row.setSubmittedAt(local(NOW.minusSeconds(120)));
        return row;
    }

    private static MembershipDO activeMembership(long id, long userId, long schoolId, long campusId,
                                                  long sourceVerificationId, long scopeVersion) {
        MembershipDO row = new MembershipDO();
        row.setId(id);
        row.setUserId(userId);
        row.setSchoolId(schoolId);
        row.setCampusId(campusId);
        row.setSourceVerificationId(sourceVerificationId);
        row.setStatus("ACTIVE");
        row.setEffectiveFrom(local(NOW.minusSeconds(60)));
        row.setExpiresAt(local(EXPIRY));
        row.setScopeVersion(scopeVersion);
        return row;
    }

    private static VerificationDO copy(VerificationDO source) {
        if (source == null) return null;
        VerificationDO copy = baseVerification(source.getId(), source.getUserId(), source.getSchoolId(), source.getCampusId());
        copy.setMethod(source.getMethod());
        copy.setEvidenceRefsJson(source.getEvidenceRefsJson());
        copy.setProofReference(source.getProofReference());
        copy.setStatus(source.getStatus());
        copy.setVersion(source.getVersion());
        copy.setSubmittedAt(source.getSubmittedAt());
        copy.setReviewedAt(source.getReviewedAt());
        copy.setReviewedBy(source.getReviewedBy());
        copy.setReviewReason(source.getReviewReason());
        copy.setValidFrom(source.getValidFrom());
        copy.setExpiresAt(source.getExpiresAt());
        return copy;
    }

    private static MembershipDO copy(MembershipDO source) {
        if (source == null) return null;
        MembershipDO copy = activeMembership(source.getId(), source.getUserId(), source.getSchoolId(),
                source.getCampusId(), source.getSourceVerificationId(), source.getScopeVersion());
        copy.setStatus(source.getStatus());
        copy.setEffectiveFrom(source.getEffectiveFrom());
        copy.setExpiresAt(source.getExpiresAt());
        copy.setRevokedAt(source.getRevokedAt());
        copy.setRevokedBy(source.getRevokedBy());
        copy.setRevocationReason(source.getRevocationReason());
        return copy;
    }

    private static VerificationReviewDO copy(VerificationReviewDO source) {
        VerificationReviewDO copy = new VerificationReviewDO();
        copy.setId(source.getId());
        copy.setVerificationId(source.getVerificationId());
        copy.setFromStatus(source.getFromStatus());
        copy.setToStatus(source.getToStatus());
        copy.setAction(source.getAction());
        copy.setOperatorId(source.getOperatorId());
        copy.setReason(source.getReason());
        copy.setVerificationVersion(source.getVerificationVersion());
        copy.setOccurredAt(source.getOccurredAt());
        return copy;
    }

    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static final class TestState {
        private VerificationDO verification;
        private MembershipDO membership;
        private List<VerificationReviewDO> reviews = new ArrayList<>();
        private List<CampusEventEnvelope> events = new ArrayList<>();
        private final InMemoryIdempotencyRepository idempotency = new InMemoryIdempotencyRepository();
        private boolean failOutbox;

        Snapshot snapshot() {
            return new Snapshot(copy(verification), copy(membership), new ArrayList<>(reviews),
                    new ArrayList<>(events), new HashMap<>(idempotency.records));
        }

        void restore(Snapshot snapshot) {
            verification = snapshot.verification;
            membership = snapshot.membership;
            reviews = snapshot.reviews;
            events = snapshot.events;
            idempotency.records.clear();
            idempotency.records.putAll(snapshot.idempotencyRecords);
        }
    }

    private record Snapshot(VerificationDO verification, MembershipDO membership,
                            List<VerificationReviewDO> reviews, List<CampusEventEnvelope> events,
                            Map<IdempotencyKey, IdempotencyRecord> idempotencyRecords) {
    }

    private static final class SnapshotTransactions implements TransactionOperations {
        private final TestState state;

        private SnapshotTransactions(TestState state) {
            this.state = state;
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            Snapshot snapshot = state.snapshot();
            try {
                return action.doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
            } catch (RuntimeException | Error failure) {
                state.restore(snapshot);
                throw failure;
            }
        }
    }

    private static final class InMemoryIdempotencyRepository implements IdempotencyRepository {
        private final Map<IdempotencyKey, IdempotencyRecord> records = new HashMap<>();
        private final AtomicInteger ids = new AtomicInteger();

        @Override
        public IdempotencyReservation reserve(IdempotencyKey key, String requestHash, Instant expiresAt,
                                              String processingToken) {
            IdempotencyRecord existing = records.get(key);
            if (existing != null) {
                return new IdempotencyReservation(existing, false);
            }
            IdempotencyRecord created = new IdempotencyRecord(ids.incrementAndGet(), key, requestHash,
                    IdempotencyStatus.PROCESSING, processingToken, null, null, 1, expiresAt);
            records.put(key, created);
            return new IdempotencyReservation(created, true);
        }

        @Override
        public void complete(IdempotencyKey key, String processingToken, String businessReference,
                             long resultVersion, String resultJson) {
            IdempotencyRecord existing = records.get(key);
            records.put(key, new IdempotencyRecord(existing.id(), key, existing.requestHash(),
                    IdempotencyStatus.SUCCEEDED, processingToken, businessReference, resultJson,
                    resultVersion, existing.expiresAt()));
        }
    }
}
