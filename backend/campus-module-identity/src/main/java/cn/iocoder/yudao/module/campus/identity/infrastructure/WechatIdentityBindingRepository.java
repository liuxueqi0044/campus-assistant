package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.WechatIdentityBindingDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql.WechatIdentityBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Persistence adapter deliberately exposes only trusted identity lookup operations. */
@Repository
@RequiredArgsConstructor
public class WechatIdentityBindingRepository {

    private final WechatIdentityBindingMapper mapper;

    public WechatIdentityBindingDO findByAppIdAndOpenId(String appId, String openId) {
        return mapper.selectOne(WechatIdentityBindingDO::getAppId, appId,
                WechatIdentityBindingDO::getOpenId, openId);
    }

    public WechatIdentityBindingDO findByAppIdAndUnionId(String appId, String unionId) {
        if (unionId == null) {
            return null;
        }
        return mapper.selectOne(WechatIdentityBindingDO::getAppId, appId,
                WechatIdentityBindingDO::getUnionId, unionId);
    }

    public void insert(WechatIdentityBindingDO binding) {
        mapper.insert(binding);
    }
}
