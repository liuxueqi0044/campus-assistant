package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("campus_operator_grant_audit")
@TenantIgnore // Campus scope is enforced by campus policy, not the upstream SaaS tenant column.
@Data
public class OperatorGrantAuditDO {

    @TableId
    private Long id;
    private Long grantId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String reason;
    private Long grantVersion;
    private String snapshotJson;
    private LocalDateTime occurredAt;
    private LocalDateTime createTime;
}
