package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("campus_account_lifecycle_log")
@TenantIgnore // Campus scope is enforced by campus policy, not the upstream SaaS tenant column.
@Data
public class AccountLifecycleLogDO {

    @TableId
    private Long id;
    private Long userId;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private String actorType;
    private Long actorId;
    private Long lifecycleVersion;
    private Long authVersion;
    private LocalDateTime occurredAt;
}
