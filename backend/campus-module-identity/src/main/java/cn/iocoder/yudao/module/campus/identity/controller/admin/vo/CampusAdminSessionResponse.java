package cn.iocoder.yudao.module.campus.identity.controller.admin.vo;

import cn.iocoder.yudao.module.campus.common.api.json.CampusTimestamp;
import cn.iocoder.yudao.module.campus.identity.application.CampusAdminSessionResult;
import lombok.Value;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Value
public class CampusAdminSessionResponse {
    String userId;
    String accessToken;
    String refreshToken;
    @CampusTimestamp
    OffsetDateTime expiresAt;

    public static CampusAdminSessionResponse from(CampusAdminSessionResult result) {
        return new CampusAdminSessionResponse(String.valueOf(result.userId()), result.accessToken(),
                result.refreshToken(), result.expiresTime().atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }
}
