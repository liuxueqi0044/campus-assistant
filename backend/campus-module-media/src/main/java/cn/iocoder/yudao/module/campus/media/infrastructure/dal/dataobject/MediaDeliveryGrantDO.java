package cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** A short-lived proxy-download capability. Its raw opaque token is never persisted. */
@TableName("campus_media_delivery_grant")
@TenantIgnore // Campus scope is enforced by campus policy, not the upstream SaaS tenant column.
@Data
@EqualsAndHashCode(callSuper = true)
public class MediaDeliveryGrantDO extends BaseDO {

    @TableId
    private Long id;
    private String tokenHash;
    private String actorType;
    private Long actorId;
    private Long assetId;
    private Long assetGeneration;
    private Long assetVersion;
    private Long bindingId;
    private String caseReference;
    private String reason;
    private LocalDateTime expiresAt;
    private Long version;
}
