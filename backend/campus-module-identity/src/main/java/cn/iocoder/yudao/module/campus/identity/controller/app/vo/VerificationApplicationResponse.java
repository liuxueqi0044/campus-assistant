package cn.iocoder.yudao.module.campus.identity.controller.app.vo;

import cn.iocoder.yudao.module.campus.common.api.json.CampusIdString;
import cn.iocoder.yudao.module.campus.identity.application.verification.VerificationApplicationResult;
import lombok.Value;

@Value
public class VerificationApplicationResponse {
    @CampusIdString
    String applicationId;
    String status;
    long version;

    public static VerificationApplicationResponse from(VerificationApplicationResult result) {
        return new VerificationApplicationResponse(result.applicationId(), result.status(), result.version());
    }
}
