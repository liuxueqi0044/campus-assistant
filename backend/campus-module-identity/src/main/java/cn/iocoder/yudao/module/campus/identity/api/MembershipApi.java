package cn.iocoder.yudao.module.campus.identity.api;

public interface MembershipApi {

    MembershipCheckResult check(long userId, long schoolId, long campusId, String action);
}
