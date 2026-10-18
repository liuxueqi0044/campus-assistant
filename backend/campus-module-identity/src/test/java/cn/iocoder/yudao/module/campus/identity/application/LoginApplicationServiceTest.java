package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.api.IdentityApi;
import cn.iocoder.yudao.module.campus.identity.application.port.AccountStatusPort;
import cn.iocoder.yudao.module.campus.identity.infrastructure.MemberAccountAdapter;
import cn.iocoder.yudao.module.campus.identity.infrastructure.PlatformTenantExecutor;
import cn.iocoder.yudao.module.campus.identity.infrastructure.WechatIdentity;
import cn.iocoder.yudao.module.campus.identity.infrastructure.WechatIdentityAdapter;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class LoginApplicationServiceTest {

    @Mock private WechatIdentityAdapter identityAdapter;
    @Mock private WechatIdentityBindingService bindingService;
    @Mock private MemberAccountAdapter memberAccountAdapter;
    @Mock private AccountStatusPort accountStatusPort;

    private LoginApplicationService service;

    @BeforeEach
    void setUp() {
        service = new LoginApplicationService(identityAdapter, bindingService, memberAccountAdapter, accountStatusPort,
                new PlatformTenantExecutor(1L));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void issuesMemberTokenOnlyAfterTrustedExchangeAndBinding() {
        WechatIdentity identity = new WechatIdentity("server-app", "trusted-openid", null);
        MemberUserDO member = member(7L);
        OAuth2AccessTokenRespDTO token = token();
        when(identityAdapter.exchangeCode("single-use-code")).thenReturn(identity);
        when(bindingService.resolveOrCreateMember(identity)).thenReturn(7L);
        when(memberAccountAdapter.getRequiredActiveMember(7L)).thenReturn(member);
        when(memberAccountAdapter.issueMemberToken(member)).thenReturn(token);

        IdentityApi.IdentityLoginResult result = service.loginWithWechatCode("single-use-code");

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.accessToken()).isEqualTo("member-access-token");
        assertThat(result.expiresAt().getOffset().getTotalSeconds()).isEqualTo(8 * 3600);
        assertThat(result.profileCompletion()).isTrue();
        verify(identityAdapter).exchangeCode("single-use-code");
        verify(bindingService).resolveOrCreateMember(identity);
        verify(accountStatusPort).assertActive(7L);
        verify(memberAccountAdapter).issueMemberToken(member);
    }

    @Test
    void duplicateBindingReadsWinnerAfterLosingTransactionRollsBack() {
        WechatIdentity identity = new WechatIdentity("server-app", "same-openid", null);
        DuplicateKeyException duplicate = new DuplicateKeyException("unique");
        MemberUserDO winner = member(9L);
        when(identityAdapter.exchangeCode("code-a")).thenReturn(identity);
        when(bindingService.resolveOrCreateMember(identity)).thenThrow(duplicate);
        when(bindingService.resolveConcurrentWinner(eq(identity), eq(duplicate))).thenReturn(9L);
        when(memberAccountAdapter.getRequiredActiveMember(9L)).thenReturn(winner);
        when(memberAccountAdapter.issueMemberToken(winner)).thenReturn(token());

        IdentityApi.IdentityLoginResult result = service.loginWithWechatCode("code-a");

        assertThat(result.userId()).isEqualTo(9L);
        verify(bindingService).resolveConcurrentWinner(identity, duplicate);
    }

    @Test
    void invalidOrUnavailableProviderResultNeverCreatesMemberOrToken() {
        CampusApiException unavailable = new CampusApiException(CampusErrorCodes.IDENTITY_PROVIDER_UNAVAILABLE);
        when(identityAdapter.exchangeCode("unavailable-code")).thenThrow(unavailable);

        assertThat(org.assertj.core.api.ThrowableAssert.catchThrowable(
                () -> service.loginWithWechatCode("unavailable-code"))).isSameAs(unavailable);

        verify(bindingService, never()).resolveOrCreateMember(any());
        verify(memberAccountAdapter, never()).issueMemberToken(any());
    }

    @Test
    void disabledMemberNeverReceivesToken() {
        WechatIdentity identity = new WechatIdentity("server-app", "disabled-openid", null);
        CampusApiException disabled = new CampusApiException(CampusErrorCodes.ACCOUNT_DISABLED);
        when(identityAdapter.exchangeCode("valid-code")).thenReturn(identity);
        when(bindingService.resolveOrCreateMember(identity)).thenReturn(5L);
        when(memberAccountAdapter.getRequiredActiveMember(5L)).thenThrow(disabled);

        assertThat(org.assertj.core.api.ThrowableAssert.catchThrowable(
                () -> service.loginWithWechatCode("valid-code"))).isSameAs(disabled);

        verify(memberAccountAdapter, never()).issueMemberToken(any());
    }

    @Test
    void lifecycleSuspensionBlocksNewTokenEvenWhenUpstreamMemberRemainsEnabled() {
        WechatIdentity identity = new WechatIdentity("server-app", "suspended-openid", null);
        MemberUserDO member = member(5L);
        CampusApiException suspended = new CampusApiException(CampusErrorCodes.ACCOUNT_DISABLED);
        when(identityAdapter.exchangeCode("valid-code")).thenReturn(identity);
        when(bindingService.resolveOrCreateMember(identity)).thenReturn(5L);
        when(memberAccountAdapter.getRequiredActiveMember(5L)).thenReturn(member);
        doThrow(suspended).when(accountStatusPort).assertActive(5L);

        assertThat(org.assertj.core.api.ThrowableAssert.catchThrowable(
                () -> service.loginWithWechatCode("valid-code"))).isSameAs(suspended);

        verify(memberAccountAdapter, never()).issueMemberToken(any());
    }

    @Test
    void exchangesCodeOutsideButRunsBindingAndTokenInFixedPlatformTenantThenRestoresCallerContext() {
        TenantContextHolder.setTenantId(777L);
        TenantContextHolder.setIgnore(true);
        WechatIdentity identity = new WechatIdentity("server-app", "trusted-openid", null);
        MemberUserDO member = member(7L);
        when(identityAdapter.exchangeCode("code")).thenAnswer(invocation -> {
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(777L);
            assertThat(TenantContextHolder.isIgnore()).isTrue();
            return identity;
        });
        doAnswer(invocation -> {
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(1L);
            assertThat(TenantContextHolder.isIgnore()).isFalse();
            return 7L;
        }).when(bindingService).resolveOrCreateMember(identity);
        when(memberAccountAdapter.getRequiredActiveMember(7L)).thenReturn(member);
        when(memberAccountAdapter.issueMemberToken(member)).thenReturn(token());

        service.loginWithWechatCode("code");

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(777L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
    }

    private static MemberUserDO member(Long id) {
        MemberUserDO member = new MemberUserDO();
        member.setId(id);
        member.setName("已完成资料");
        return member;
    }

    private static OAuth2AccessTokenRespDTO token() {
        OAuth2AccessTokenRespDTO token = new OAuth2AccessTokenRespDTO();
        token.setAccessToken("member-access-token");
        token.setExpiresTime(LocalDateTime.of(2026, 9, 17, 12, 0));
        return token;
    }
}
