package cn.iocoder.yudao.module.campus.identity.controller.admin.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record VerificationDetailResponse(String applicationId, String schoolId, String campusId,
                                         String method, String status, long version,
                                         OffsetDateTime submittedAt, List<Evidence> evidence) {
    public record Evidence(String assetId) {
    }
}
