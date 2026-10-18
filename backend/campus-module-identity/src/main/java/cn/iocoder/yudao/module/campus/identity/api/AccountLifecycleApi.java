package cn.iocoder.yudao.module.campus.identity.api;

public interface AccountLifecycleApi {

    AccountLifecycleSnapshot snapshot(long userId);

    void assertAllowed(long userId, AccountCapability capability);
}
