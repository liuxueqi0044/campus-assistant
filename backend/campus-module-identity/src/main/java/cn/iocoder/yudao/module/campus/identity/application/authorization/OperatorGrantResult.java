package cn.iocoder.yudao.module.campus.identity.application.authorization;

public record OperatorGrantResult(long grantId, String status, long version,
                                  long authorizationVersion) {
}
