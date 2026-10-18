package cn.iocoder.yudao.module.campus.identity.application.resource;

import cn.iocoder.yudao.module.campus.identity.api.AccountLifecycleApi;
import cn.iocoder.yudao.module.campus.identity.api.ActorRef;
import cn.iocoder.yudao.module.campus.identity.api.ActorType;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAccessPurpose;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAction;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAuthorizationDecision;
import cn.iocoder.yudao.module.campus.identity.api.ResourcePolicyProvider;
import cn.iocoder.yudao.module.campus.identity.api.ResourceRef;
import cn.iocoder.yudao.module.campus.identity.application.account.AccountLifecycleException;
import cn.iocoder.yudao.module.campus.identity.application.account.AccountLifecycleFailure;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ResourceAuthorizationServiceTest {

    private final AccountLifecycleApi lifecycleApi = mock(AccountLifecycleApi.class);

    @Test
    void unknownResourceTypeAndMissingActorAreDeniedWithoutProviderCall() {
        ResourcePolicyProvider provider = provider("TIMETABLE", true);
        ResourceAuthorizationService service = new ResourceAuthorizationService(List.of(provider), lifecycleApi);

        assertThat(service.authorize(new ActorRef(ActorType.MEMBER, 1), resource("PRIVATE_MEDIA"),
                ResourceAction.READ, ResourceAccessPurpose.ownerUse("VIEW"))).isEqualTo(
                ResourceAuthorizationDecision.deny());
        assertThat(service.authorize(null, resource("TIMETABLE"), ResourceAction.READ,
                ResourceAccessPurpose.ownerUse("VIEW"))).isEqualTo(ResourceAuthorizationDecision.deny());
        verify(provider, never()).authorize(any(), any(), any(), any());
    }

    @Test
    void timetableOwnerStubForU2NeverAllowsU1() {
        ResourcePolicyProvider timetable = new ResourcePolicyProvider() {
            @Override
            public String resourceType() {
                return "TIMETABLE";
            }

            @Override
            public ResourceAuthorizationDecision authorize(ActorRef actor, ResourceRef resource,
                                                           ResourceAction action, ResourceAccessPurpose purpose) {
                return actor.type() == ActorType.MEMBER && actor.id() == 2
                        ? ResourceAuthorizationDecision.allow(Set.of("OWNER"))
                        : ResourceAuthorizationDecision.deny();
            }
        };
        ResourceAuthorizationService service = new ResourceAuthorizationService(List.of(timetable), lifecycleApi);

        assertThat(service.authorize(new ActorRef(ActorType.MEMBER, 1), resource("TIMETABLE"),
                ResourceAction.READ, ResourceAccessPurpose.ownerUse("VIEW")).allowed()).isFalse();
        assertThat(service.authorize(new ActorRef(ActorType.MEMBER, 2), resource("TIMETABLE"),
                ResourceAction.READ, ResourceAccessPurpose.ownerUse("VIEW")).allowed()).isTrue();
    }

    @Test
    void suspendedOldSessionIsDeniedBeforeDomainPolicyRuns() {
        ResourcePolicyProvider provider = provider("TIMETABLE", true);
        doThrow(new AccountLifecycleException(AccountLifecycleFailure.ACCOUNT_ACTION_RESTRICTED))
                .when(lifecycleApi).assertAllowed(anyLong(), any());
        ResourceAuthorizationService service = new ResourceAuthorizationService(List.of(provider), lifecycleApi);

        ResourceAuthorizationDecision decision = service.authorize(new ActorRef(ActorType.MEMBER, 1),
                resource("TIMETABLE"), ResourceAction.READ, ResourceAccessPurpose.ownerUse("VIEW"));

        assertThat(decision.allowed()).isFalse();
        verify(provider, never()).authorize(any(), any(), any(), any());
    }

    @Test
    void operatorPrivateAccessRequiresCaseAndReasonEvenIfProviderWouldAllow() {
        ResourcePolicyProvider provider = provider("PRIVATE_MEDIA", true);
        ResourceAuthorizationService service = new ResourceAuthorizationService(List.of(provider), lifecycleApi);

        assertThat(service.authorize(new ActorRef(ActorType.OPERATOR, 901), resource("PRIVATE_MEDIA"),
                ResourceAction.READ, ResourceAccessPurpose.ownerUse("REVIEW")).allowed()).isFalse();
        verify(provider, never()).authorize(any(), any(), any(), any());
    }

    @Test
    void duplicateProviderTypeFailsStartupInsteadOfChoosingOne() {
        assertThatThrownBy(() -> new ResourceAuthorizationService(
                List.of(provider("TIMETABLE", true), provider("TIMETABLE", false)), lifecycleApi))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ResourcePolicyProvider provider(String type, boolean allow) {
        ResourcePolicyProvider provider = mock(ResourcePolicyProvider.class);
        org.mockito.Mockito.when(provider.resourceType()).thenReturn(type);
        org.mockito.Mockito.when(provider.authorize(any(), any(), any(), any())).thenReturn(allow
                ? ResourceAuthorizationDecision.allow(Set.of("FIXTURE"))
                : ResourceAuthorizationDecision.deny());
        return provider;
    }

    private static ResourceRef resource(String type) {
        return new ResourceRef(type, "fixture-1", 1L);
    }
}
