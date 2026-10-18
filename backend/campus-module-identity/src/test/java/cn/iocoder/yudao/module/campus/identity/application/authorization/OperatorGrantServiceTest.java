package cn.iocoder.yudao.module.campus.identity.application.authorization;

import cn.iocoder.yudao.module.campus.identity.api.CampusScopeApi;
import cn.iocoder.yudao.module.campus.identity.api.OperatorAction;
import cn.iocoder.yudao.module.campus.identity.application.port.OperatorAccountPort;
import cn.iocoder.yudao.module.campus.identity.domain.authorization.OperatorScopeType;
import cn.iocoder.yudao.module.campus.identity.infrastructure.OperatorGrantRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.SchoolRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.OperatorGrantAuditDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.OperatorGrantDO;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperatorGrantServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");

    private final OperatorGrantRepository repository = mock(OperatorGrantRepository.class);
    private final OperatorAccountPort operatorAccountPort = mock(OperatorAccountPort.class);
    private final CampusScopeApi campusScopeApi = mock(CampusScopeApi.class);
    private final SchoolRepository schoolRepository = mock(SchoolRepository.class);
    private final GrantState state = new GrantState();
    private OperatorGrantService service;

    @BeforeEach
    void setUp() {
        when(operatorAccountPort.hasPermission(anyLong(), anyString())).thenReturn(true);
        wireRepository();
        service = new OperatorGrantService(repository, operatorAccountPort, campusScopeApi, schoolRepository,
                new SnapshotTransactions(state), new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createWritesGrantAuditAndAuthorizationVersionAtomically() {
        OperatorGrantResult result = service.grant(900, new GrantOperatorCommand(
                901, OperatorScopeType.CAMPUS, 1L, 11L,
                Set.of(OperatorAction.MEMBERSHIP_REVOKE, OperatorAction.VERIFICATION_REVIEW),
                NOW.minusSeconds(60), NOW.plusSeconds(3600), 0, "initial assignment"));

        assertThat(result.grantId()).isEqualTo(77);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.authorizationVersion()).isEqualTo(1);
        assertThat(state.grant.getActionsJson())
                .isEqualTo("[\"MEMBERSHIP_REVOKE\",\"VERIFICATION_REVIEW\"]");
        assertThat(state.audits).singleElement().satisfies(audit -> {
            assertThat(audit.getAction()).isEqualTo("GRANT");
            assertThat(audit.getFromStatus()).isEqualTo("NONE");
            assertThat(audit.getToStatus()).isEqualTo("ACTIVE");
            assertThat(audit.getSnapshotJson()).doesNotContain("mobile", "email", "token");
        });
    }

    @Test
    void staleVersionCannotOverwriteGrantOrAppendAudit() {
        state.grant = activeGrant(77, 901, 2);
        state.authorizationVersion = 4;
        OperatorGrantDO before = copy(state.grant);

        assertThatThrownBy(() -> service.grant(900, new GrantOperatorCommand(
                901, OperatorScopeType.CAMPUS, 1L, 11L,
                Set.of(OperatorAction.MEMBERSHIP_REVOKE),
                NOW.minusSeconds(60), NOW.plusSeconds(3600), 1, "stale update")))
                .isInstanceOfSatisfying(OperatorAuthorizationException.class,
                        error -> assertThat(error.failure()).isEqualTo(OperatorAuthorizationFailure.GRANT_CONFLICT));

        assertThat(state.grant).usingRecursiveComparison().isEqualTo(before);
        assertThat(state.audits).isEmpty();
        assertThat(state.authorizationVersion).isEqualTo(4);
    }

    @Test
    void authorizationVersionFailureRollsBackGrantAndAudit() {
        state.failVersionBump = true;

        assertThatThrownBy(() -> service.grant(900, new GrantOperatorCommand(
                901, OperatorScopeType.CAMPUS, 1L, 11L,
                Set.of(OperatorAction.VERIFICATION_REVIEW),
                NOW.minusSeconds(60), NOW.plusSeconds(3600), 0, "will roll back")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced authorization version failure");

        assertThat(state.grant).isNull();
        assertThat(state.audits).isEmpty();
        assertThat(state.authorizationVersion).isZero();
    }

    @Test
    void revokeUpdatesStatusAuditAndAuthorizationVersion() {
        state.grant = activeGrant(77, 901, 1);
        state.authorizationVersion = 1;

        OperatorGrantResult result = service.revoke(900,
                new RevokeOperatorGrantCommand(77, 1, "assignment withdrawn"));

        assertThat(result.status()).isEqualTo("REVOKED");
        assertThat(result.version()).isEqualTo(2);
        assertThat(result.authorizationVersion()).isEqualTo(2);
        assertThat(state.grant.getRevokedBy()).isEqualTo(900);
        assertThat(state.audits).singleElement().satisfies(audit -> {
            assertThat(audit.getAction()).isEqualTo("REVOKE");
            assertThat(audit.getFromStatus()).isEqualTo("ACTIVE");
            assertThat(audit.getToStatus()).isEqualTo("REVOKED");
        });
    }

    private void wireRepository() {
        when(repository.lockScope(anyLong(), anyString(), any(), any())).thenAnswer(invocation -> state.grant);
        when(repository.lockGrant(anyLong())).thenAnswer(invocation -> state.grant != null
                && state.grant.getId().equals(invocation.<Long>getArgument(0)) ? state.grant : null);
        doAnswer(invocation -> {
            OperatorGrantDO inserted = invocation.getArgument(0);
            inserted.setId(77L);
            state.grant = inserted;
            return null;
        }).when(repository).insertGrant(any());
        when(repository.activateGrant(anyLong(), anyString(), any(), any(), anyLong(), anyLong(), anyLong()))
                .thenReturn(true);
        when(repository.revokeGrant(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), any()))
                .thenReturn(true);
        doAnswer(invocation -> {
            OperatorGrantAuditDO audit = invocation.getArgument(0);
            state.audits.add(copy(audit));
            return null;
        }).when(repository).appendAudit(any());
        when(repository.bumpAuthorizationVersion(anyLong())).thenAnswer(invocation -> {
            if (state.failVersionBump) {
                throw new IllegalStateException("forced authorization version failure");
            }
            return ++state.authorizationVersion;
        });
    }

    private static OperatorGrantDO activeGrant(long id, long operatorId, long version) {
        OperatorGrantDO row = new OperatorGrantDO();
        row.setId(id);
        row.setOperatorId(operatorId);
        row.setScopeType("CAMPUS");
        row.setSchoolId(1L);
        row.setCampusId(11L);
        row.setActionsJson("[\"VERIFICATION_REVIEW\"]");
        row.setStatus("ACTIVE");
        row.setValidFrom(local(NOW.minusSeconds(60)));
        row.setExpiresAt(local(NOW.plusSeconds(3600)));
        row.setVersion(version);
        row.setGrantedBy(900L);
        return row;
    }

    private static OperatorGrantDO copy(OperatorGrantDO source) {
        if (source == null) return null;
        OperatorGrantDO copy = new OperatorGrantDO();
        copy.setId(source.getId());
        copy.setOperatorId(source.getOperatorId());
        copy.setScopeType(source.getScopeType());
        copy.setSchoolId(source.getSchoolId());
        copy.setCampusId(source.getCampusId());
        copy.setActionsJson(source.getActionsJson());
        copy.setStatus(source.getStatus());
        copy.setValidFrom(source.getValidFrom());
        copy.setExpiresAt(source.getExpiresAt());
        copy.setVersion(source.getVersion());
        copy.setGrantedBy(source.getGrantedBy());
        copy.setRevokedAt(source.getRevokedAt());
        copy.setRevokedBy(source.getRevokedBy());
        copy.setRevocationReason(source.getRevocationReason());
        return copy;
    }

    private static OperatorGrantAuditDO copy(OperatorGrantAuditDO source) {
        OperatorGrantAuditDO copy = new OperatorGrantAuditDO();
        copy.setGrantId(source.getGrantId());
        copy.setAction(source.getAction());
        copy.setFromStatus(source.getFromStatus());
        copy.setToStatus(source.getToStatus());
        copy.setOperatorId(source.getOperatorId());
        copy.setReason(source.getReason());
        copy.setGrantVersion(source.getGrantVersion());
        copy.setSnapshotJson(source.getSnapshotJson());
        copy.setOccurredAt(source.getOccurredAt());
        return copy;
    }

    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static final class GrantState {
        private OperatorGrantDO grant;
        private List<OperatorGrantAuditDO> audits = new ArrayList<>();
        private long authorizationVersion;
        private boolean failVersionBump;

        Snapshot snapshot() {
            return new Snapshot(copy(grant), audits.stream().map(OperatorGrantServiceTest::copy).toList(),
                    authorizationVersion);
        }

        void restore(Snapshot snapshot) {
            grant = snapshot.grant;
            audits = new ArrayList<>(snapshot.audits);
            authorizationVersion = snapshot.authorizationVersion;
        }
    }

    private record Snapshot(OperatorGrantDO grant, List<OperatorGrantAuditDO> audits,
                            long authorizationVersion) {
    }

    private static final class SnapshotTransactions implements TransactionOperations {
        private final GrantState state;

        private SnapshotTransactions(GrantState state) {
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
}
