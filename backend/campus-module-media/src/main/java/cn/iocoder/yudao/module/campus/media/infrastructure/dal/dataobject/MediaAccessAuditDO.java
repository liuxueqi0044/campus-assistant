package cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** Deliberately metadata-only: no token, object key, URL, or private bytes appear here. */
@TableName("campus_media_access_log")
@TenantIgnore // Campus scope is enforced by campus policy, not the upstream SaaS tenant column.
@Data
@EqualsAndHashCode(callSuper = true)
public class MediaAccessAuditDO extends BaseDO {

    @TableId
    private Long id;
    private String actorType;
    private Long actorId;
    private Long assetId;
    private Long assetGeneration;
    private Long assetVersion;
    private Long bindingId;
    private String resourceType;
    private String resourceId;
    private String action;
    private String purpose;
    private String decision;
    private String caseReference;
    private String reason;
    private String auditLabelsJson;
    private String traceId;
    private LocalDateTime occurredAt;
}
