package cn.iocoder.yudao.module.campus.media.infrastructure;

import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAccessAuditDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaDeliveryGrantDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.mysql.MediaAccessAuditMapper;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.mysql.MediaDeliveryGrantMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaDeliveryWriteRepositoryTest {

    @Test
    void failsClosedWhenGrantInsertDoesNotWriteExactlyOneRow() {
        MediaDeliveryGrantMapper mapper = mock(MediaDeliveryGrantMapper.class);
        when(mapper.insertGrant(org.mockito.ArgumentMatchers.any())).thenReturn(0);

        assertThatThrownBy(() -> new MediaDeliveryGrantRepository(mapper).insert(new MediaDeliveryGrantDO()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsClosedWhenAuditInsertDoesNotWriteExactlyOneRow() {
        MediaAccessAuditMapper mapper = mock(MediaAccessAuditMapper.class);
        when(mapper.insertAudit(org.mockito.ArgumentMatchers.any())).thenReturn(2);

        assertThatThrownBy(() -> new MediaAccessAuditRepository(mapper).insert(new MediaAccessAuditDO()))
                .isInstanceOf(IllegalStateException.class);
    }
}
