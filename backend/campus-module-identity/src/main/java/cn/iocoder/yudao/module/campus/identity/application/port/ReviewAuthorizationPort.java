package cn.iocoder.yudao.module.campus.identity.application.port;

public interface ReviewAuthorizationPort {

    void assertAllowed(long operatorId, long schoolId, long campusId, String action);
}
