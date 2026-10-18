package cn.iocoder.yudao.module.campus.identity.application.resource;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.identity.api.AccountCapability;
import cn.iocoder.yudao.module.campus.identity.api.AccountLifecycleApi;
import cn.iocoder.yudao.module.campus.identity.api.ActorRef;
import cn.iocoder.yudao.module.campus.identity.api.ActorType;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAccessPurpose;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAction;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAuthorizationApi;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAuthorizationDecision;
import cn.iocoder.yudao.module.campus.identity.api.ResourcePolicyProvider;
import cn.iocoder.yudao.module.campus.identity.api.ResourceRef;
import cn.iocoder.yudao.module.campus.identity.application.account.AccountLifecycleException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ResourceAuthorizationService implements ResourceAuthorizationApi {

    private final Map<String, ResourcePolicyProvider> providers;
    private final AccountLifecycleApi accountLifecycleApi;

    public ResourceAuthorizationService(List<ResourcePolicyProvider> providers,
                                        AccountLifecycleApi accountLifecycleApi) {
        Objects.requireNonNull(providers, "providers");
        this.accountLifecycleApi = Objects.requireNonNull(accountLifecycleApi, "accountLifecycleApi");
        Map<String, ResourcePolicyProvider> indexed = new LinkedHashMap<>();
        for (ResourcePolicyProvider provider : providers) {
            Objects.requireNonNull(provider, "resource policy provider");
            String type = requireType(provider.resourceType());
            if (indexed.putIfAbsent(type, provider) != null) {
                throw new IllegalStateException("duplicate resource policy provider for " + type);
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    @Override
    public ResourceAuthorizationDecision authorize(ActorRef actor, ResourceRef resource,
                                                   ResourceAction action, ResourceAccessPurpose purpose) {
        if (actor == null || resource == null || action == null || purpose == null) {
            return ResourceAuthorizationDecision.deny();
        }
        ResourcePolicyProvider provider = providers.get(resource.type());
        if (provider == null) {
            return ResourceAuthorizationDecision.deny();
        }
        if (actor.type() == ActorType.MEMBER && !accountAllows(actor.id(), action)) {
            return ResourceAuthorizationDecision.deny();
        }
        if (actor.type() == ActorType.OPERATOR
                && (purpose.caseReference() == null || purpose.reason() == null)) {
            return ResourceAuthorizationDecision.deny();
        }
        ResourceAuthorizationDecision decision = provider.authorize(actor, resource, action, purpose);
        return decision == null ? ResourceAuthorizationDecision.deny() : decision;
    }

    private boolean accountAllows(long userId, ResourceAction action) {
        AccountCapability capability = switch (action) {
            case READ, SUBSCRIBE, EXPORT -> AccountCapability.PRIVATE_RESOURCE_READ;
            case UPDATE, DELETE -> AccountCapability.NEW_SENSITIVE_ACTION;
        };
        try {
            accountLifecycleApi.assertAllowed(userId, capability);
            return true;
        } catch (AccountLifecycleException | CampusApiException denied) {
            return false;
        }
    }

    private static String requireType(String value) {
        String type = Objects.requireNonNull(value, "resourceType").trim();
        if (type.isEmpty() || type.length() > 64) {
            throw new IllegalArgumentException("resourceType must contain 1..64 characters");
        }
        return type;
    }
}
