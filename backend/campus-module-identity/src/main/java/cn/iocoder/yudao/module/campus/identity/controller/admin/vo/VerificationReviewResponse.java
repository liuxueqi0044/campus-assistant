package cn.iocoder.yudao.module.campus.identity.controller.admin.vo;

import cn.iocoder.yudao.module.campus.common.api.json.CampusIdString;
import cn.iocoder.yudao.module.campus.identity.application.verification.VerificationReviewResult;
import lombok.Value;

@Value
public class VerificationReviewResponse {
    @CampusIdString
    String applicationId;
    String status;
    long version;
    @CampusIdString
    String membershipId;
    Long scopeVersion;

    public static VerificationReviewResponse from(VerificationReviewResult result) {
        return new VerificationReviewResponse(result.applicationId(), result.status(), result.version(),
                result.membershipId(), result.scopeVersion());
    }
}
