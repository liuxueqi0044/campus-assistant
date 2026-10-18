package cn.iocoder.yudao.module.campus.identity.application.resource;

import cn.iocoder.yudao.module.campus.identity.api.ActorRef;
import cn.iocoder.yudao.module.campus.identity.api.ActorType;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAccessPurpose;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAction;
import cn.iocoder.yudao.module.campus.identity.api.ResourceAuthorizationDecision;
import cn.iocoder.yudao.module.campus.identity.api.ResourceRef;
import cn.iocoder.yudao.module.campus.identity.application.MembershipVerificationException;
import cn.iocoder.yudao.module.campus.identity.application.MembershipVerificationFailure;
import cn.iocoder.yudao.module.campus.identity.application.port.ReviewAuthorizationPort;
import cn.iocoder.yudao.module.campus.identity.infrastructure.MembershipVerificationRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.VerificationDO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MembershipVerificationProofPolicyProviderTest {

    private final MembershipVerificationRepository repository = mock(MembershipVerificationRepository.class);
    private final ReviewAuthorizationPort reviewAuthorization = mock(ReviewAuthorizationPort.class);
    private final MembershipVerificationProofPolicyProvider provider =
            new MembershipVerificationProofPolicyProvider(repository, reviewAuthorization);

    @Test
    void ownerCanReadOnlyTheExactProof() {
        when(repository.findVerificationByProofReference("proof-1")).thenReturn(verification());

        ResourceAuthorizationDecision owner = provider.authorize(new ActorRef(ActorType.MEMBER, 7),
                resource("proof-1"), ResourceAction.READ,
                ResourceAccessPurpose.ownerUse("MEMBERSHIP_VERIFICATION"));
        ResourceAuthorizationDecision other = provider.authorize(new ActorRef(ActorType.MEMBER, 8),
                resource("proof-1"), ResourceAction.READ,
                ResourceAccessPurpose.ownerUse("MEMBERSHIP_VERIFICATION"));

        assertThat(owner.allowed()).isTrue();
        assertThat(owner.auditLabels()).containsExactly("OWNER");
        assertThat(other.allowed()).isFalse();
    }

    @Test
    void scopedReviewerRequiresCaseContextAndVerificationReviewGrant() {
        when(repository.findVerificationByProofReference("proof-1")).thenReturn(verification());
        ActorRef operator = new ActorRef(ActorType.OPERATOR, 90);

        ResourceAuthorizationDecision missingCase = provider.authorize(operator, resource("proof-1"),
                ResourceAction.READ, ResourceAccessPurpose.ownerUse("MEMBERSHIP_VERIFICATION"));
        ResourceAuthorizationDecision allowed = provider.authorize(operator, resource("proof-1"),
                ResourceAction.READ, new ResourceAccessPurpose("MEMBERSHIP_VERIFICATION", "51", "review"));

        assertThat(missingCase.allowed()).isFalse();
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.auditLabels()).containsExactlyInAnyOrder("SCHOOL_SCOPED", "VERIFICATION_REVIEW");
        verify(reviewAuthorization).assertAllowed(90, 3, 5, "VERIFICATION_REVIEW");
    }

    @Test
    void crossSchoolOrDeniedReviewerGetsTheSameHiddenDecision() {
        when(repository.findVerificationByProofReference("proof-1")).thenReturn(verification());
        doThrow(new MembershipVerificationException(MembershipVerificationFailure.VERIFICATION_REVIEW_FORBIDDEN))
                .when(reviewAuthorization).assertAllowed(90, 3, 5, "VERIFICATION_REVIEW");

        ResourceAuthorizationDecision wrongScope = provider.authorize(new ActorRef(ActorType.OPERATOR, 90),
                new ResourceRef("MEMBERSHIP_VERIFICATION_PROOF", "proof-1", 4L), ResourceAction.READ,
                new ResourceAccessPurpose("MEMBERSHIP_VERIFICATION", "51", "review"));
        ResourceAuthorizationDecision denied = provider.authorize(new ActorRef(ActorType.OPERATOR, 90),
                resource("proof-1"), ResourceAction.READ,
                new ResourceAccessPurpose("MEMBERSHIP_VERIFICATION", "51", "review"));

        assertThat(wrongScope).isEqualTo(ResourceAuthorizationDecision.deny());
        assertThat(denied).isEqualTo(ResourceAuthorizationDecision.deny());
    }

    @Test
    void reviewerCannotAttributeProofAccessToAnotherApplication() {
        when(repository.findVerificationByProofReference("proof-1")).thenReturn(verification());

        ResourceAuthorizationDecision denied = provider.authorize(new ActorRef(ActorType.OPERATOR, 90),
                resource("proof-1"), ResourceAction.READ,
                new ResourceAccessPurpose("MEMBERSHIP_VERIFICATION", "52", "review"));

        assertThat(denied).isEqualTo(ResourceAuthorizationDecision.deny());
        verifyNoInteractions(reviewAuthorization);
    }

    @Test
    void unknownProofActionOrPurposeFailsClosed() {
        assertThat(provider.authorize(new ActorRef(ActorType.MEMBER, 7), resource("unknown"), ResourceAction.READ,
                ResourceAccessPurpose.ownerUse("MEMBERSHIP_VERIFICATION")).allowed()).isFalse();
        when(repository.findVerificationByProofReference("proof-1")).thenReturn(verification());
        assertThat(provider.authorize(new ActorRef(ActorType.MEMBER, 7), resource("proof-1"), ResourceAction.UPDATE,
                ResourceAccessPurpose.ownerUse("MEMBERSHIP_VERIFICATION")).allowed()).isFalse();
        assertThat(provider.authorize(new ActorRef(ActorType.MEMBER, 7), resource("proof-1"), ResourceAction.READ,
                ResourceAccessPurpose.ownerUse("CHAT_ATTACHMENT")).allowed()).isFalse();
    }

    private static VerificationDO verification() {
        VerificationDO row = new VerificationDO();
        row.setId(51L);
        row.setUserId(7L);
        row.setSchoolId(3L);
        row.setCampusId(5L);
        row.setProofReference("proof-1");
        row.setStatus("PENDING");
        return row;
    }

    private static ResourceRef resource(String proofReference) {
        return new ResourceRef("MEMBERSHIP_VERIFICATION_PROOF", proofReference, null);
    }
}
