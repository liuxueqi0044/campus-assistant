package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.module.campus.identity.api.IdentityApi;
import cn.iocoder.yudao.module.campus.identity.infrastructure.MemberAccountAdapter;
import cn.iocoder.yudao.module.campus.identity.infrastructure.PlatformTenantExecutor;
import cn.iocoder.yudao.module.campus.identity.infrastructure.WechatIdentity;
import cn.iocoder.yudao.module.campus.identity.infrastructure.WechatIdentityAdapter;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Application orchestration: remote exchange is intentionally outside the database transaction. */
@Service
@RequiredArgsConstructor
public class LoginApplicationService implements IdentityApi {

    private final WechatIdentityAdapter wechatIdentityAdapter;
    private final WechatIdentityBindingService bindingService;
    private final MemberAccountAdapter memberAccountAdapter;
    private final PlatformTenantExecutor platformTenantExecutor;

    @Override
    public IdentityLoginResult loginWithWechatCode(String code) {
        // A one-time provider code exchange must never keep a database transaction or tenant scope open.
        WechatIdentity identity = wechatIdentityAdapter.exchangeCode(code);
        return platformTenantExecutor.execute(() -> loginAfterExchange(identity));
    }

    @Override
    public void logout(String token) {
        platformTenantExecutor.execute(() -> {
            memberAccountAdapter.logout(token);
            return null;
        });
    }

    private IdentityLoginResult loginAfterExchange(WechatIdentity identity) {
        Long memberId;
        try {
            memberId = bindingService.resolveOrCreateMember(identity);
        } catch (DataIntegrityViolationException duplicate) {
            memberId = bindingService.resolveConcurrentWinner(identity, duplicate);
        }
        MemberUserDO member = memberAccountAdapter.getRequiredActiveMember(memberId);
        OAuth2AccessTokenRespDTO token = memberAccountAdapter.issueMemberToken(member);
        return new IdentityLoginResult(member.getId(), token.getAccessToken(),
                token.getExpiresTime().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime(),
                member.getName() != null && !member.getName().isBlank());
    }
}
