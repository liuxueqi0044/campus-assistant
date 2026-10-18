package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.module.campus.identity.api.CampusScopeApi;
import cn.iocoder.yudao.module.campus.identity.api.MembershipApi;
import cn.iocoder.yudao.module.campus.identity.api.MembershipCheckResult;
import cn.iocoder.yudao.module.campus.identity.application.port.AccountStatusPort;
import cn.iocoder.yudao.module.campus.identity.domain.membership.Membership;
import cn.iocoder.yudao.module.campus.identity.domain.membership.MembershipEvaluation;
import cn.iocoder.yudao.module.campus.identity.domain.membership.MembershipStatus;
import cn.iocoder.yudao.module.campus.identity.infrastructure.MembershipVerificationRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.MembershipDO;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
public class MembershipService implements MembershipApi {

    private final MembershipVerificationRepository repository;
    private final CampusScopeApi campusScopeApi;
    private final AccountStatusPort accountStatusPort;
    private final Clock clock;

    public MembershipService(MembershipVerificationRepository repository, CampusScopeApi campusScopeApi,
                             AccountStatusPort accountStatusPort, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.campusScopeApi = Objects.requireNonNull(campusScopeApi);
        this.accountStatusPort = Objects.requireNonNull(accountStatusPort);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public MembershipCheckResult check(long userId, long schoolId, long campusId, String action) {
        if (userId <= 0 || schoolId <= 0 || campusId <= 0 || action == null || action.isBlank()) {
            throw new IllegalArgumentException("membership check requires positive ids and a non-blank action");
        }
        accountStatusPort.assertActive(userId);
        campusScopeApi.assertCampusBelongsToSchool(schoolId, campusId);
        MembershipDO row = repository.findMembership(userId, schoolId);
        if (row == null) {
            throw failure(MembershipVerificationFailure.MEMBERSHIP_REQUIRED);
        }
        Membership membership = toDomain(row);
        MembershipEvaluation evaluation = membership.evaluate(campusId, clock);
        if (evaluation == MembershipEvaluation.REVOKED) {
            throw failure(MembershipVerificationFailure.MEMBERSHIP_REVOKED);
        }
        if (evaluation == MembershipEvaluation.EXPIRED) {
            throw failure(MembershipVerificationFailure.MEMBERSHIP_EXPIRED);
        }
        if (evaluation != MembershipEvaluation.EFFECTIVE) {
            throw failure(MembershipVerificationFailure.MEMBERSHIP_REQUIRED);
        }
        return new MembershipCheckResult(true,
                OffsetDateTime.ofInstant(membership.expiresAt(), ZoneOffset.UTC), membership.scopeVersion());
    }

    private static Membership toDomain(MembershipDO row) {
        return new Membership(row.getId(), row.getUserId(), row.getSchoolId(), row.getCampusId(),
                MembershipStatus.valueOf(row.getStatus()), row.getEffectiveFrom().toInstant(ZoneOffset.UTC),
                row.getExpiresAt().toInstant(ZoneOffset.UTC), row.getScopeVersion());
    }

    private static MembershipVerificationException failure(MembershipVerificationFailure failure) {
        return new MembershipVerificationException(failure);
    }
}
