package cn.iocoder.yudao.module.campus.identity.api;

public interface ResourceAuthorizationApi {

    ResourceAuthorizationDecision authorize(ActorRef actor, ResourceRef resource, ResourceAction action,
                                            ResourceAccessPurpose purpose);
}
