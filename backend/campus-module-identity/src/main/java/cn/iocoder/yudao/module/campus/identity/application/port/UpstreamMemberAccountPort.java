package cn.iocoder.yudao.module.campus.identity.application.port;

/** Upstream member remains the identity authority; M01.06 only adds lifecycle restrictions. */
public interface UpstreamMemberAccountPort {

    void assertActive(long userId);
}
