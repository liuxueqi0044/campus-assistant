package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OAuthSessionRevocationAdapterTest {

    @Test
    void revokesAllMemberTokensByServerOwnedUserType() {
        OAuth2TokenCommonApi tokenApi = mock(OAuth2TokenCommonApi.class);
        OAuthSessionRevocationAdapter adapter = new OAuthSessionRevocationAdapter(tokenApi);

        adapter.revokeAll(7);

        verify(tokenApi).removeAccessToken(7L, UserTypeEnum.MEMBER.getValue());
    }
}
