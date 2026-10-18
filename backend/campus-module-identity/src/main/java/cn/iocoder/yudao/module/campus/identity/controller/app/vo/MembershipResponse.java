package cn.iocoder.yudao.module.campus.identity.controller.app.vo;

import cn.iocoder.yudao.module.campus.common.api.json.CampusTimestamp;
import cn.iocoder.yudao.module.campus.identity.api.MembershipCheckResult;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
public class MembershipResponse {
    boolean effective;
    @CampusTimestamp
    OffsetDateTime expiresAt;
    long scopeVersion;

    public static MembershipResponse from(MembershipCheckResult result) {
        return new MembershipResponse(result.effective(), result.expiresAt(), result.scopeVersion());
    }
}
