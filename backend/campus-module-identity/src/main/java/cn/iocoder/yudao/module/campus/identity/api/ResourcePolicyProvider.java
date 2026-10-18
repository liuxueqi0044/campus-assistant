package cn.iocoder.yudao.module.campus.identity.api;

/** Implemented by the domain that owns a resource; identity owns registration and fail-closed dispatch only. */
public interface ResourcePolicyProvider {

    String resourceType();

    ResourceAuthorizationDecision authorize(ActorRef actor, ResourceRef resource, ResourceAction action,
                                            ResourceAccessPurpose purpose);
}
