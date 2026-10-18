package cn.iocoder.yudao.module.campus.identity.application.authorization;

import cn.iocoder.yudao.module.campus.identity.api.AuthorizedSchoolQuery;
import cn.iocoder.yudao.module.campus.identity.api.AuthorizedSchoolScope;
import cn.iocoder.yudao.module.campus.identity.api.OperatorAction;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchoolScopeQueryGuardTest {

    private final SchoolScopeQueryGuard guard = new SchoolScopeQueryGuard();

    @Test
    void missingAuthorizationProofIsDenied() {
        assertFailure(() -> guard.require(null, OperatorAction.VERIFICATION_REVIEW),
                OperatorAuthorizationFailure.SCOPE_DENIED);
    }

    @Test
    void proofForAnotherActionCannotCompileAQuery() {
        assertFailure(() -> guard.require(proof(), OperatorAction.MEMBERSHIP_REVOKE),
                OperatorAuthorizationFailure.ACTION_DENIED);
    }

    @Test
    void validProofProducesOnlyItsExactServerOwnedCondition() {
        AuthorizedSchoolQuery query = guard.require(proof(), OperatorAction.VERIFICATION_REVIEW);

        assertThat(query.schoolId()).isEqualTo(1);
        assertThat(query.campusId()).isEqualTo(11);
        assertThat(query.grantId()).isEqualTo(7);
        assertThat(query.authorizationVersion()).isEqualTo(2);
    }

    @Test
    void unknownGrantScopeTypeIsDenied() {
        AuthorizedSchoolScope unknown = new AuthorizedSchoolScope(901, OperatorAction.VERIFICATION_REVIEW,
                1, null, 7, "UNKNOWN", 1, 2,
                OffsetDateTime.parse("2027-09-17T09:00:00Z"));
        assertFailure(() -> guard.require(unknown, OperatorAction.VERIFICATION_REVIEW),
                OperatorAuthorizationFailure.SCOPE_DENIED);
    }

    @Test
    void schoolGrantMayCarryRequestedCampusCondition() {
        AuthorizedSchoolScope school = new AuthorizedSchoolScope(901, OperatorAction.VERIFICATION_REVIEW,
                1, 11L, 7, "SCHOOL", 1, 2,
                OffsetDateTime.parse("2027-09-17T09:00:00Z"));
        assertThat(guard.require(school, OperatorAction.VERIFICATION_REVIEW).campusId()).isEqualTo(11L);
    }

    @Test
    void proofAtExpiryIsDenied() {
        SchoolScopeQueryGuard fixed = new SchoolScopeQueryGuard(
                Clock.fixed(java.time.Instant.parse("2027-09-17T09:00:00Z"), ZoneOffset.UTC));
        AuthorizedSchoolScope expired = new AuthorizedSchoolScope(901, OperatorAction.VERIFICATION_REVIEW,
                1, 11L, 7, "CAMPUS", 1, 2,
                OffsetDateTime.parse("2027-09-17T09:00:00Z"));
        assertFailure(() -> fixed.require(expired, OperatorAction.VERIFICATION_REVIEW),
                OperatorAuthorizationFailure.SCOPE_DENIED);
    }

    private static AuthorizedSchoolScope proof() {
        return new AuthorizedSchoolScope(901, OperatorAction.VERIFICATION_REVIEW, 1, 11L,
                7, "CAMPUS", 1, 2, OffsetDateTime.parse("2027-09-17T09:00:00Z"));
    }

    private static void assertFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
                                      OperatorAuthorizationFailure expected) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(OperatorAuthorizationException.class,
                error -> assertThat(error.failure()).isEqualTo(expected));
    }
}
