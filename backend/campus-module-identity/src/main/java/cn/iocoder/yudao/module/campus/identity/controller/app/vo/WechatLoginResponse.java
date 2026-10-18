package cn.iocoder.yudao.module.campus.identity.controller.app.vo;

import cn.iocoder.yudao.module.campus.identity.api.IdentityApi;
import cn.iocoder.yudao.module.campus.common.api.json.CampusTimestamp;
import lombok.Value;

import java.time.OffsetDateTime;

/** Campus response intentionally excludes refresh token, openid and all raw channel material. */
@Value
public class WechatLoginResponse {
    String accessToken;
    @CampusTimestamp
    OffsetDateTime expiresAt;
    String userId;
    boolean profileCompletion;

    public static WechatLoginResponse from(IdentityApi.IdentityLoginResult result) {
        return new WechatLoginResponse(result.accessToken(), result.expiresAt(), String.valueOf(result.userId()),
                result.profileCompletion());
    }
}
