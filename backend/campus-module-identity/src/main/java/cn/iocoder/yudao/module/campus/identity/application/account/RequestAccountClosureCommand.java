package cn.iocoder.yudao.module.campus.identity.application.account;

public record RequestAccountClosureCommand(String reason, long expectedVersion) {
}
