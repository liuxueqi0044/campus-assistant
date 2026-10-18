package cn.iocoder.yudao.module.campus.identity.application.verification;

public record ReviewVerificationCommand(long verificationId, ReviewDecision decision, String reason,
                                        long expectedVersion, String idempotencyKey) {
}
