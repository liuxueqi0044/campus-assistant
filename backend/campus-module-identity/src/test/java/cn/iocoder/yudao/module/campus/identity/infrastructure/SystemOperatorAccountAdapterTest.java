package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.campus.identity.application.authorization.OperatorAuthorizationException;
import cn.iocoder.yudao.module.campus.identity.application.authorization.OperatorAuthorizationFailure;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemOperatorAccountAdapterTest {

    private final AdminUserApi adminUserApi = mock(AdminUserApi.class);
    private final PermissionCommonApi permissionApi = mock(PermissionCommonApi.class);
    private final SystemOperatorAccountAdapter adapter = new SystemOperatorAccountAdapter(adminUserApi, permissionApi);

    @Test
    void disabledOrMissingAdminFailsClosed() {
        AdminUserRespDTO disabled = new AdminUserRespDTO();
        disabled.setId(901L);
        disabled.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(adminUserApi.getUser(901L)).thenReturn(disabled);

        assertThatThrownBy(() -> adapter.assertActive(901))
                .isInstanceOfSatisfying(OperatorAuthorizationException.class,
                        error -> assertThat(error.failure()).isEqualTo(OperatorAuthorizationFailure.OPERATOR_DISABLED));
        when(adminUserApi.getUser(902L)).thenReturn(null);
        assertThatThrownBy(() -> adapter.assertActive(902))
                .isInstanceOf(OperatorAuthorizationException.class);
        AdminUserRespDTO unknown = new AdminUserRespDTO();
        unknown.setId(903L);
        unknown.setStatus(99);
        when(adminUserApi.getUser(903L)).thenReturn(unknown);
        assertThatThrownBy(() -> adapter.assertActive(903))
                .isInstanceOf(OperatorAuthorizationException.class);
    }

    @Test
    void permissionAlwaysDelegatesToUpstreamRbac() {
        when(permissionApi.hasAnyPermissions(901L, "campus:verification:review")).thenReturn(true);

        assertThat(adapter.hasPermission(901, "campus:verification:review")).isTrue();
        verify(permissionApi).hasAnyPermissions(901L, "campus:verification:review");
    }
}
