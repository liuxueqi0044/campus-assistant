package cn.iocoder.yudao.module.campus.identity.controller.admin.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record VerificationQueueResponse(List<Item> list, long total, int page, int pageSize) {
    public record Item(String applicationId, String schoolId, String campusId, String method,
                       String status, long version, OffsetDateTime submittedAt) {
    }
}
