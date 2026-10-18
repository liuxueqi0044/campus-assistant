package cn.iocoder.yudao.module.campus.identity.application;

import java.time.LocalDateTime;

/** Server-owned platform-admin session returned through the campus contract. */
public record CampusAdminSessionResult(long userId, String accessToken, String refreshToken,
                                       LocalDateTime expiresTime) {
}
