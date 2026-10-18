package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.module.campus.identity.application.port.AccountStatusPort;
import cn.iocoder.yudao.module.campus.identity.application.port.ReviewAuthorizationPort;
import cn.iocoder.yudao.module.campus.identity.application.port.VerificationEvidencePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Fail-closed production defaults until M01.04/M01.06/M02 provide real adapters. */
@Configuration(proxyBeanMethods = false)
public class MembershipVerificationConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock campusServerClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(AccountStatusPort.class)
    public AccountStatusPort unavailableAccountStatusPort() {
        return userId -> {
            throw new MembershipVerificationException(MembershipVerificationFailure.ACCOUNT_DISABLED);
        };
    }

    @Bean
    @ConditionalOnMissingBean(ReviewAuthorizationPort.class)
    public ReviewAuthorizationPort unavailableReviewAuthorizationPort() {
        return (operatorId, schoolId, campusId, action) -> {
            throw new MembershipVerificationException(
                    MembershipVerificationFailure.VERIFICATION_REVIEW_FORBIDDEN);
        };
    }

    @Bean
    @ConditionalOnMissingBean(VerificationEvidencePort.class)
    public VerificationEvidencePort unavailableVerificationEvidencePort() {
        return (userId, schoolId, campusId, method, evidenceRefs) -> {
            throw new MembershipVerificationException(
                    MembershipVerificationFailure.VERIFICATION_METHOD_DISABLED);
        };
    }
}
