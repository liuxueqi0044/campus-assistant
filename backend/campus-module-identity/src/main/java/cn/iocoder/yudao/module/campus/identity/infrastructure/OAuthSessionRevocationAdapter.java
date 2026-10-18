package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.campus.identity.application.port.SessionRevocationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuthSessionRevocationAdapter implements SessionRevocationPort {

    private final OAuth2TokenCommonApi oauth2TokenApi;

    @Override
    public void revokeAll(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        oauth2TokenApi.removeAccessToken(userId, UserTypeEnum.MEMBER.getValue());
    }
}
