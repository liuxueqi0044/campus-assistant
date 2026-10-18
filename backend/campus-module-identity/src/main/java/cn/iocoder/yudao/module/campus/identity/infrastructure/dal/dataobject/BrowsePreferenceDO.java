package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** A user's display preference only; this is intentionally not a membership relation. */
@TableName("campus_user_browse_preference")
@Data
@EqualsAndHashCode(callSuper = true)
public class BrowsePreferenceDO extends BaseDO {

    @TableId
    private Long id;
    private Long userId;
    private Long schoolId;
    private Long campusId;
}
