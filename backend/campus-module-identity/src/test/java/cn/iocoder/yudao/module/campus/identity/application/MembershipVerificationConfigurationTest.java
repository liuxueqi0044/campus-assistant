package cn.iocoder.yudao.module.campus.identity.application;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MembershipVerificationConfigurationTest {

    @Test
    void absentRealAdaptersInstallFailClosedPorts() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                MembershipVerificationConfiguration.class)) {
            assertThatThrownBy(() -> context.getBean(
                            cn.iocoder.yudao.module.campus.identity.application.port.AccountStatusPort.class)
                    .assertActive(101))
                    .isInstanceOfSatisfying(MembershipVerificationException.class,
                            error -> assertThat(error.failure())
                                    .isEqualTo(MembershipVerificationFailure.ACCOUNT_DISABLED));
            assertThatThrownBy(() -> context.getBean(
                            cn.iocoder.yudao.module.campus.identity.application.port.ReviewAuthorizationPort.class)
                    .assertAllowed(901, 1, "VERIFICATION_REVIEW"))
                    .isInstanceOfSatisfying(MembershipVerificationException.class,
                            error -> assertThat(error.failure())
                                    .isEqualTo(MembershipVerificationFailure.VERIFICATION_REVIEW_FORBIDDEN));
            assertThatThrownBy(() -> context.getBean(
                            cn.iocoder.yudao.module.campus.identity.application.port.VerificationEvidencePort.class)
                    .verify(101, 1, 11, "STUDENT_CARD", List.of("asset-a")))
                    .isInstanceOfSatisfying(MembershipVerificationException.class,
                            error -> assertThat(error.failure())
                                    .isEqualTo(MembershipVerificationFailure.VERIFICATION_METHOD_DISABLED));
        }
    }
}
