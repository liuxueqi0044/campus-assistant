package cn.iocoder.yudao.module.campus.identity.application.verification;

import java.util.List;

public record SubmitVerificationCommand(long schoolId, long campusId, String method,
                                        List<String> evidenceRefs, String idempotencyKey) {
}
