package cn.iocoder.yudao.module.campus.identity.application.port;

public interface SessionRevocationPort {

    void revokeAll(long userId);
}
