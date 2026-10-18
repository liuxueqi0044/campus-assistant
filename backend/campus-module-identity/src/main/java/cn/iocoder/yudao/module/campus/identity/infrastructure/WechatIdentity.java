package cn.iocoder.yudao.module.campus.identity.infrastructure;

/** Trusted result of a server-side WeChat code exchange. Never construct this from client JSON. */
public record WechatIdentity(String appId, String openId, String unionId) {
}
