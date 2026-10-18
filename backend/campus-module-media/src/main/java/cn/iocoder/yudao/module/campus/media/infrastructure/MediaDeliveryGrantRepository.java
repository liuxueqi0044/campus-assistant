package cn.iocoder.yudao.module.campus.media.infrastructure;

import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaDeliveryGrantDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.mysql.MediaDeliveryGrantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class MediaDeliveryGrantRepository {

    private final MediaDeliveryGrantMapper mapper;

    public void insert(MediaDeliveryGrantDO grant) {
        if (mapper.insertGrant(grant) != 1) {
            throw new IllegalStateException("media delivery grant insert did not affect exactly one row");
        }
    }

    public MediaDeliveryGrantDO findActiveByTokenHash(String tokenHash, LocalDateTime now) {
        return mapper.selectActiveByTokenHash(tokenHash, now);
    }
}
