package cn.iocoder.yudao.module.campus.identity.infrastructure;

/** Boundary for one-time WeChat code exchange; implementations must not log credential material. */
public interface WechatIdentityAdapter {

    WechatIdentity exchangeCode(String code);
}
