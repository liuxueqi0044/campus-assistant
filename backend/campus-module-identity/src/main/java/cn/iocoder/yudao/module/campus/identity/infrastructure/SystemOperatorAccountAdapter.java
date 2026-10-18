package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.campus.identity.application.authorization.OperatorAuthorizationException;
import cn.iocoder.yudao.module.campus.identity.application.authorization.OperatorAuthorizationFailure;
import cn.iocoder.yudao.module.campus.identity.application.port.OperatorAccountPort;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Reuses the upstream admin account and RBAC authorities; both are mandatory. */
@Component
@RequiredArgsConstructor
public class SystemOperatorAccountAdapter implements OperatorAccountPort {

    private final AdminUserApi adminUserApi;
    private final PermissionCommonApi permissionApi;

    @Override
    public void assertActive(long operatorId) {
        AdminUserRespDTO operator = operatorId <= 0 ? null : adminUserApi.getUser(operatorId);
        if (operator == null || !Objects.equals(operator.getStatus(), CommonStatusEnum.ENABLE.getStatus())) {
            throw new OperatorAuthorizationException(OperatorAuthorizationFailure.OPERATOR_DISABLED);
        }
    }

    @Override
    public boolean hasPermission(long operatorId, String permission) {
        return operatorId > 0 && permission != null && !permission.isBlank()
                && permissionApi.hasAnyPermissions(operatorId, permission);
    }
}
