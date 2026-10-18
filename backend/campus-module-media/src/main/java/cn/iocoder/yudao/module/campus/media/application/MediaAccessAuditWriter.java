package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.media.infrastructure.MediaAccessAuditRepository;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAccessAuditDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists deny evidence independently because the caller deliberately follows it by throwing. */
@Service
@RequiredArgsConstructor
public class MediaAccessAuditWriter {

    private final MediaAccessAuditRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendDenied(MediaAccessAuditDO audit) {
        repository.insert(audit);
    }
}
