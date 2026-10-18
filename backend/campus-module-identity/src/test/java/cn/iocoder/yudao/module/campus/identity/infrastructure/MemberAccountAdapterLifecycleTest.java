package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.auth.MemberAuthService;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.api.logger.LoginLogApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberAccountAdapterLifecycleTest {

    private final MemberUserService users = mock(MemberUserService.class);
    private final MemberAccountAdapter adapter = new MemberAccountAdapter(users,
            mock(OAuth2TokenCommonApi.class), mock(LoginLogApi.class), mock(MemberAuthService.class));

    @Test
    void onlyExactUpstreamEnabledStatusIsAccepted() {
        MemberUserDO enabled = new MemberUserDO().setId(7L).setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(users.getUser(7L)).thenReturn(enabled);

        assertThat(adapter.getRequiredActiveMember(7L)).isSameAs(enabled);
    }

    @Test
    void unknownUpstreamStatusFailsClosed() {
        when(users.getUser(7L)).thenReturn(new MemberUserDO().setId(7L).setStatus(99));

        assertThatThrownBy(() -> adapter.getRequiredActiveMember(7L))
                .isInstanceOf(CampusApiException.class);
    }
}
