package cn.iocoder.yudao.module.campus.identity.api;

import java.time.OffsetDateTime;

public record MembershipCheckResult(boolean effective, OffsetDateTime expiresAt, long scopeVersion) {
}
