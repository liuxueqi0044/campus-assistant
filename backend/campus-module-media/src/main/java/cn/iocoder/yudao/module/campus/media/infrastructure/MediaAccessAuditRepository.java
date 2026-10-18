package cn.iocoder.yudao.module.campus.media.infrastructure;

import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAccessAuditDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.mysql.MediaAccessAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MediaAccessAuditRepository {

    private final MediaAccessAuditMapper mapper;

    public void insert(MediaAccessAuditDO audit) {
        if (mapper.insertAudit(audit) != 1) {
            throw new IllegalStateException("media access audit insert did not affect exactly one row");
        }
    }
}
