package cn.iocoder.yudao.module.campus.identity.application.authorization;

public record RevokeOperatorGrantCommand(long grantId, long expectedVersion, String reason) {
}
