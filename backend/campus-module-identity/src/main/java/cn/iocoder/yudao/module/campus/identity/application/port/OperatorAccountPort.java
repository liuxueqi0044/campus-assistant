package cn.iocoder.yudao.module.campus.identity.application.port;

public interface OperatorAccountPort {

    void assertActive(long operatorId);

    boolean hasPermission(long operatorId, String permission);
}
