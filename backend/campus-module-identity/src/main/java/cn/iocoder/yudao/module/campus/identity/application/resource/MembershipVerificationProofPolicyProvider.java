package cn.iocoder.yudao.module.campus.identity.application.resource;

import cn.iocoder.yudao.module.campus.identity.api.ActorRef;
import cn.iocoder.yudao.module.campus.identity.api.ActorType;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAccessPurpose;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAction;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAuthorizationDecision;
import cn.iocoder.yudao.module.campus.identity.api.ResourcePolicyProvider;
import cn.iocoder.yudao.module.campus.identity.api.ResourceRef;
import cn.iocoder.yudao.module.campus.identity.application.MembershipVerificationException;
import cn.iocoder.yudao.module.campus.identity.application.port.ReviewAuthorizationPort;
import cn.iocoder.yudao.module.campus.identity.infrastructure.MembershipVerificationRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.VerificationDO;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/** Resolves an opaque proof reference to its owning verification before private evidence can be read. */
@Component
public class MembershipVerificationProofPolicyProvider implements ResourcePolicyProvider {

    public static final String RESOURCE_TYPE = "MEMBERSHIP_VERIFICATION_PROOF";
    public static final String PURPOSE = "MEMBERSHIP_VERIFICATION";

    private final MembershipVerificationRepository repository;
    private final ReviewAuthorizationPort reviewAuthorization;

    public MembershipVerificationProofPolicyProvider(MembershipVerificationRepository repository,
                                                     ReviewAuthorizationPort reviewAuthorization) {
        this.repository = Objects.requireNonNull(repository);
        this.reviewAuthorization = Objects.requireNonNull(reviewAuthorization);
    }

    @Override
    public String resourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public ResourceAuthorizationDecision authorize(ActorRef actor, ResourceRef resource,
                                                   ResourceAction action, ResourceAccessPurpose purpose) {
        if (actor == null || resource == null || action != ResourceAction.READ || purpose == null
                || !RESOURCE_TYPE.equals(resource.type()) || !PURPOSE.equals(purpose.purpose())) {
            return ResourceAuthorizationDecision.deny();
        }
        VerificationDO verification = repository.findVerificationByProofReference(resource.id());
        if (verification == null || !Objects.equals(verification.getProofReference(), resource.id())
                || resource.schoolId() != null && !Objects.equals(resource.schoolId(), verification.getSchoolId())) {
            return ResourceAuthorizationDecision.deny();
        }
        if (actor.type() == ActorType.MEMBER) {
            return Objects.equals(verification.getUserId(), actor.id())
                    ? ResourceAuthorizationDecision.allow(Set.of("OWNER"))
                    : ResourceAuthorizationDecision.deny();
        }
        if (actor.type() != ActorType.OPERATOR || purpose.caseReference() == null || purpose.reason() == null
                || !String.valueOf(verification.getId()).equals(purpose.caseReference())) {
            return ResourceAuthorizationDecision.deny();
        }
        try {
            reviewAuthorization.assertAllowed(actor.id(), verification.getSchoolId(), verification.getCampusId(),
                    "VERIFICATION_REVIEW");
            return ResourceAuthorizationDecision.allow(Set.of("SCHOOL_SCOPED", "VERIFICATION_REVIEW"));
        } catch (MembershipVerificationException denied) {
            return ResourceAuthorizationDecision.deny();
        }
    }
}
