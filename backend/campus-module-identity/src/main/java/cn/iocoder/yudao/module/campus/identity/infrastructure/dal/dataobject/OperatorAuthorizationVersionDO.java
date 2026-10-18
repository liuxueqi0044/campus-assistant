package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("campus_operator_authorization_version")
@TenantIgnore // Campus scope is enforced by campus policy, not the upstream SaaS tenant column.
@Data
public class OperatorAuthorizationVersionDO {

    @TableId
    private Long operatorId;
    private Long version;
    private LocalDateTime updateTime;
}
