package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipVerificationExceptionTest {

    @Test
    void everyInternalFailureMapsToTheFrozenPublicRegistry() {
        Map<MembershipVerificationFailure, CampusErrorSpec> expected = Map.ofEntries(
                Map.entry(MembershipVerificationFailure.MEDIA_NOT_OWNED, CampusErrorCodes.MEDIA_NOT_OWNED),
                Map.entry(MembershipVerificationFailure.MEDIA_NOT_READY, CampusErrorCodes.MEDIA_NOT_READY),
                Map.entry(MembershipVerificationFailure.VERIFICATION_CONFLICT, CampusErrorCodes.VERIFICATION_CONFLICT),
                Map.entry(MembershipVerificationFailure.MEMBERSHIP_REQUIRED, CampusErrorCodes.MEMBERSHIP_REQUIRED),
                Map.entry(MembershipVerificationFailure.MEMBERSHIP_EXPIRED, CampusErrorCodes.MEMBERSHIP_EXPIRED),
                Map.entry(MembershipVerificationFailure.MEMBERSHIP_REVOKED, CampusErrorCodes.MEMBERSHIP_REVOKED),
                Map.entry(MembershipVerificationFailure.VERIFICATION_METHOD_DISABLED,
                        CampusErrorCodes.VERIFICATION_METHOD_DISABLED),
                Map.entry(MembershipVerificationFailure.VERIFICATION_REVIEW_FORBIDDEN,
                        CampusErrorCodes.VERIFICATION_REVIEW_FORBIDDEN),
                Map.entry(MembershipVerificationFailure.ACCOUNT_DISABLED, CampusErrorCodes.ACCOUNT_DISABLED),
                Map.entry(MembershipVerificationFailure.RESOURCE_NOT_FOUND, CampusErrorCodes.RESOURCE_NOT_FOUND));

        assertThat(expected).hasSize(MembershipVerificationFailure.values().length);
        expected.forEach((failure, spec) -> {
            MembershipVerificationException exception = new MembershipVerificationException(failure);
            assertThat(exception.failure()).isEqualTo(failure);
            assertThat(exception.getSpec()).isSameAs(spec);
        });
    }
}
