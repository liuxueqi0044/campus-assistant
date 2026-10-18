package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.infrastructure.MemberAccountAdapter;
import cn.iocoder.yudao.module.campus.identity.infrastructure.WechatIdentity;
import cn.iocoder.yudao.module.campus.identity.infrastructure.WechatIdentityBindingRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.WechatIdentityBindingDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatIdentityBindingServiceTest {

    @Mock private WechatIdentityBindingRepository repository;
    @Mock private MemberAccountAdapter memberAccountAdapter;
    private WechatIdentityBindingService service;

    @BeforeEach
    void setUp() {
        service = new WechatIdentityBindingService(repository, memberAccountAdapter);
    }

    @Test
    void storesOnlyTrustedIdentityFieldsWhenCreatingBinding() {
        WechatIdentity identity = new WechatIdentity("server-app", "trusted-openid", "trusted-unionid");
        MemberUserDO member = new MemberUserDO();
        member.setId(12L);
        when(memberAccountAdapter.createWechatMember()).thenReturn(member);

        assertThat(service.resolveOrCreateMember(identity)).isEqualTo(12L);

        ArgumentCaptor<WechatIdentityBindingDO> captor = ArgumentCaptor.forClass(WechatIdentityBindingDO.class);
        verify(repository).insert(captor.capture());
        WechatIdentityBindingDO binding = captor.getValue();
        assertThat(binding.getAppId()).isEqualTo("server-app");
        assertThat(binding.getOpenId()).isEqualTo("trusted-openid");
        assertThat(binding.getUnionId()).isEqualTo("trusted-unionid");
        assertThat(binding.getMemberId()).isEqualTo(12L);
        assertThat(WechatIdentityBindingDO.class.getDeclaredFields()).extracting("name")
                .doesNotContain("code", "sessionKey", "accessToken", "refreshToken");
    }

    @Test
    void unionIdNeverSilentlyMergesAnotherOpenId() {
        WechatIdentity identity = new WechatIdentity("server-app", "new-openid", "same-unionid");
        WechatIdentityBindingDO existing = new WechatIdentityBindingDO();
        existing.setOpenId("old-openid");
        existing.setMemberId(1L);
        when(repository.findByAppIdAndUnionId("server-app", "same-unionid")).thenReturn(existing);

        assertThatThrownBy(() -> service.resolveOrCreateMember(identity))
                .isInstanceOf(CampusApiException.class)
                .extracting(error -> ((CampusApiException) error).getSpec())
                .isEqualTo(CampusErrorCodes.IDENTITY_BINDING_CONFLICT);
        verify(memberAccountAdapter, never()).createWechatMember();
        verify(repository, never()).insert(any());
    }

    @Test
    void concurrentUnionUniqueConflictBecomesDomainConflictInsteadOfLeakingDatabaseFailure() {
        WechatIdentity identity = new WechatIdentity("server-app", "new-openid", "same-unionid");
        WechatIdentityBindingDO winner = new WechatIdentityBindingDO();
        winner.setOpenId("old-openid");
        winner.setMemberId(2L);
        when(repository.findByAppIdAndUnionId("server-app", "same-unionid")).thenReturn(winner);

        assertThatThrownBy(() -> service.resolveConcurrentWinner(identity, new DuplicateKeyException("unique")))
                .isInstanceOf(CampusApiException.class)
                .extracting(error -> ((CampusApiException) error).getSpec())
                .isEqualTo(CampusErrorCodes.IDENTITY_BINDING_CONFLICT);
    }
}
