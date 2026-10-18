package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("campus_membership")
@TenantIgnore // Campus scope is enforced by campus policy, not the upstream SaaS tenant column.
@Data
@EqualsAndHashCode(callSuper = true)
public class MembershipDO extends BaseDO {

    @TableId
    private Long id;
    private Long userId;
    private Long schoolId;
    private Long campusId;
    private Long sourceVerificationId;
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime expiresAt;
    private Long scopeVersion;
    private LocalDateTime revokedAt;
    private Long revokedBy;
    private String revocationReason;
}
