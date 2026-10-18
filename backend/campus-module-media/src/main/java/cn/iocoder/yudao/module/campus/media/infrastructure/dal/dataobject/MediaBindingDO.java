package cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("campus_media_binding")
@TenantIgnore // Campus scope is enforced by campus policy, not the upstream SaaS tenant column.
@Data
@EqualsAndHashCode(callSuper = true)
public class MediaBindingDO extends BaseDO {

    @TableId
    private Long id;
    private Long assetId;
    private Long assetGeneration;
    private Long ownerId;
    private String purpose;
    private String resourceType;
    private String resourceId;
    private String contextHash;
    private LocalDateTime validFrom;
    private LocalDateTime expiresAt;
    private Long version;
}
