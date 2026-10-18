package cn.iocoder.yudao.module.campus.identity.application.port;

import java.util.List;

public interface VerificationEvidencePort {

    VerificationProof verify(long userId, long schoolId, long campusId, String method,
                             List<String> evidenceRefs);
}
