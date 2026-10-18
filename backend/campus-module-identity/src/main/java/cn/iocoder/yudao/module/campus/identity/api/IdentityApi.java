package cn.iocoder.yudao.module.campus.identity.api;

import java.time.OffsetDateTime;

/** Public, channel-neutral identity entry point for other campus modules. */
public interface IdentityApi {

    IdentityLoginResult loginWithWechatCode(String code);

    void logout(String token);

    record IdentityLoginResult(Long userId, String accessToken, OffsetDateTime expiresAt,
                               boolean profileCompletion) {
    }
}
