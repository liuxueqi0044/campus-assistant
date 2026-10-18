package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** M01.02-owned public school directory row. */
@TableName("campus_school")
@TenantIgnore // Campus scope is enforced by campus policy, not the upstream SaaS tenant column.
@Data
@EqualsAndHashCode(callSuper = true)
public class SchoolDO extends BaseDO {

    @TableId
    private Long id;
    private String code;
    private String name;
    private String timezone;
    private String status;
    private Long configVersion;
    private Integer sortOrder;
}
