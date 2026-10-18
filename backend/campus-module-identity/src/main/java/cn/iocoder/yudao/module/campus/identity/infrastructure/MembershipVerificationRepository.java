package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.MembershipDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.VerificationDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.VerificationReviewDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql.MembershipMapper;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql.VerificationMapper;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql.VerificationReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class MembershipVerificationRepository {

    private final MembershipMapper membershipMapper;
    private final VerificationMapper verificationMapper;
    private final VerificationReviewMapper reviewMapper;

    public MembershipDO findMembership(long userId, long schoolId) {
        return membershipMapper.selectByUserAndSchool(userId, schoolId);
    }

    public VerificationDO lockVerification(long verificationId) {
        return verificationMapper.selectByIdForUpdate(verificationId);
    }

    public VerificationDO findVerification(long verificationId) {
        return verificationMapper.selectApplication(verificationId);
    }

    public MembershipDO lockMembership(long userId, long schoolId) {
        return membershipMapper.selectByUserAndSchoolForUpdate(userId, schoolId);
    }

    public void insertVerification(VerificationDO verification) {
        verificationMapper.insert(verification);
    }

    public boolean transitionVerification(long verificationId, String expectedStatus, String targetStatus,
                                          long expectedVersion, long operatorId, String reason,
                                          LocalDateTime reviewedAt, LocalDateTime validFrom,
                                          LocalDateTime expiresAt) {
        return verificationMapper.transition(verificationId, expectedStatus, targetStatus, expectedVersion,
                operatorId, reason, reviewedAt, validFrom, expiresAt) == 1;
    }

    public void insertMembership(MembershipDO membership) {
        membershipMapper.insert(membership);
    }

    public boolean grantMembership(MembershipDO membership, long expectedVersion, long operatorId) {
        return membershipMapper.grant(membership.getId(), membership.getCampusId(),
                membership.getSourceVerificationId(), membership.getEffectiveFrom(), membership.getExpiresAt(),
                expectedVersion, membership.getScopeVersion(), operatorId) == 1;
    }

    public boolean revokeMembership(MembershipDO membership, long verificationId, long expectedVersion,
                                    long operatorId, String reason, LocalDateTime revokedAt) {
        return membershipMapper.revoke(membership.getId(), verificationId, expectedVersion,
                membership.getScopeVersion(), operatorId, reason, revokedAt) == 1;
    }

    public void appendReview(VerificationReviewDO review) {
        reviewMapper.insert(review);
    }
}
