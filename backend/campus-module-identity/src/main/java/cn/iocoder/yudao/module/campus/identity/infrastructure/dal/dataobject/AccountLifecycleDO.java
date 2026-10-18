package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("campus_account_lifecycle")
@TenantIgnore // Campus scope is enforced by campus policy, not the upstream SaaS tenant column.
@Data
@EqualsAndHashCode(callSuper = true)
public class AccountLifecycleDO extends BaseDO {

    @TableId
    private Long userId;
    private String status;
    private Long authVersion;
    private Long version;
    private String statusReason;
    private LocalDateTime closureRequestedAt;
    private LocalDateTime closedAt;
    private String sessionRevocationState;
    private String lastTransitionActorType;
    private Long lastTransitionBy;
}
