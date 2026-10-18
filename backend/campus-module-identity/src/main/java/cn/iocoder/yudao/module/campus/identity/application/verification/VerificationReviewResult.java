package cn.iocoder.yudao.module.campus.identity.application.verification;

public record VerificationReviewResult(String applicationId, String status, long version,
                                       String membershipId, Long scopeVersion) {
}
