package cn.iocoder.yudao.module.campus.identity.application.verification;

public record RevokeVerificationCommand(long verificationId, String reason, long expectedVersion,
                                        String idempotencyKey) {
}
