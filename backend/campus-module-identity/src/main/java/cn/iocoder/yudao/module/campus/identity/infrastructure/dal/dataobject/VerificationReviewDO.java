package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("campus_verification_review")
@TenantIgnore // Campus scope is enforced by campus policy, not the upstream SaaS tenant column.
@Data
public class VerificationReviewDO {

    @TableId
    private Long id;
    private Long verificationId;
    private String fromStatus;
    private String toStatus;
    private String action;
    private Long operatorId;
    private String reason;
    private Long verificationVersion;
    private LocalDateTime occurredAt;
    private LocalDateTime createTime;
}
