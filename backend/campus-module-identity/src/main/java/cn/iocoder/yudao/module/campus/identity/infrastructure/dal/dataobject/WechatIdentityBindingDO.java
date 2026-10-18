package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Formal M01.01 mapping: no codes, session keys or OAuth tokens are persisted here. */
@TableName("campus_wechat_identity_binding")
@TenantIgnore // global identity mapping; member_user remains scoped by PlatformTenantExecutor.
@Data
@EqualsAndHashCode(callSuper = true)
public class WechatIdentityBindingDO extends BaseDO {

    @TableId
    private Long id;
    private String appId;
    private String openId;
    private String unionId;
    private Long memberId;
}
