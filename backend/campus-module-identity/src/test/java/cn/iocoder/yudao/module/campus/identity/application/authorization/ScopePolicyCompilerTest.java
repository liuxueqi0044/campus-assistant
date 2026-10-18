package cn.iocoder.yudao.module.campus.identity.application.authorization;

import cn.iocoder.yudao.module.campus.identity.api.AuthorizedSchoolScope;
import cn.iocoder.yudao.module.campus.identity.api.OperatorAction;
import cn.iocoder.yudao.module.campus.identity.api.OperatorAuthorizationApi;
import cn.iocoder.yudao.module.campus.identity.api.RequestedSchoolScope;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScopePolicyCompilerTest {

    private final OperatorAuthorizationApi authorizationApi = mock(OperatorAuthorizationApi.class);
    private final ScopePolicyCompiler compiler = new ScopePolicyCompiler(authorizationApi);

    @Test
    void mixedSchoolBatchIsRejectedBeforeBusinessHandlerRuns() {
        RequestedSchoolScope s1 = RequestedSchoolScope.campus(1, 11);
        RequestedSchoolScope s2 = RequestedSchoolScope.campus(2, 21);
        when(authorizationApi.authorize(901, OperatorAction.MEMBERSHIP_REVOKE, s1))
                .thenReturn(proof(1, 11));
        when(authorizationApi.authorize(901, OperatorAction.MEMBERSHIP_REVOKE, s2))
                .thenThrow(new OperatorAuthorizationException(OperatorAuthorizationFailure.SCOPE_DENIED));
        AtomicBoolean handlerCalled = new AtomicBoolean();

        assertThatThrownBy(() -> compiler.executeAuthorizedBatch(901, OperatorAction.MEMBERSHIP_REVOKE,
                List.of(s1, s2), scopes -> {
                    handlerCalled.set(true);
                    return scopes.size();
                })).isInstanceOfSatisfying(OperatorAuthorizationException.class,
                        error -> assertThat(error.failure()).isEqualTo(OperatorAuthorizationFailure.SCOPE_DENIED));
        assertThat(handlerCalled).isFalse();
    }

    @Test
    void emptyBatchFailsClosed() {
        assertThatThrownBy(() -> compiler.authorizeAll(901, OperatorAction.VERIFICATION_REVIEW, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mismatchedProofCannotReachBusinessHandler() {
        RequestedSchoolScope requested = RequestedSchoolScope.campus(1, 11);
        when(authorizationApi.authorize(901, OperatorAction.VERIFICATION_REVIEW, requested))
                .thenReturn(new AuthorizedSchoolScope(901, OperatorAction.VERIFICATION_REVIEW,
                        2, 21L, 7, "CAMPUS", 1, 2,
                        OffsetDateTime.parse("2027-09-17T09:00:00Z")));
        AtomicBoolean handlerCalled = new AtomicBoolean();

        assertThatThrownBy(() -> compiler.executeAuthorizedBatch(901, OperatorAction.VERIFICATION_REVIEW,
                List.of(requested), scopes -> {
                    handlerCalled.set(true);
                    return scopes.size();
                })).isInstanceOfSatisfying(OperatorAuthorizationException.class,
                error -> assertThat(error.failure()).isEqualTo(OperatorAuthorizationFailure.SCOPE_DENIED));
        assertThat(handlerCalled).isFalse();
    }

    @Test
    void unknownProofScopeIsDeniedBeforeBusinessHandler() {
        RequestedSchoolScope requested = RequestedSchoolScope.school(1);
        AuthorizedSchoolScope unknown = new AuthorizedSchoolScope(901, OperatorAction.VERIFICATION_REVIEW,
                1, null, 7, "UNKNOWN", 1, 2, OffsetDateTime.parse("2027-09-17T09:00:00Z"));
        when(authorizationApi.authorize(901, OperatorAction.VERIFICATION_REVIEW, requested))
                .thenReturn(unknown);
        AtomicBoolean handlerCalled = new AtomicBoolean();

        assertThatThrownBy(() -> compiler.executeAuthorizedBatch(901, OperatorAction.VERIFICATION_REVIEW,
                List.of(requested), scopes -> {
                    handlerCalled.set(true);
                    return scopes.size();
                })).isInstanceOfSatisfying(OperatorAuthorizationException.class,
                error -> assertThat(error.failure()).isEqualTo(OperatorAuthorizationFailure.SCOPE_DENIED));
        assertThat(handlerCalled).isFalse();
    }

    private static AuthorizedSchoolScope proof(long schoolId, long campusId) {
        return new AuthorizedSchoolScope(901, OperatorAction.MEMBERSHIP_REVOKE, schoolId, campusId,
                7, "SCHOOL", 1, 2, OffsetDateTime.parse("2027-09-17T09:00:00Z"));
    }
}
