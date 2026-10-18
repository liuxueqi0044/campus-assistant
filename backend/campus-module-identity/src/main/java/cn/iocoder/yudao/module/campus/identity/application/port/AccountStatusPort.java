package cn.iocoder.yudao.module.campus.identity.application.port;

public interface AccountStatusPort {

    void assertActive(long userId);
}
