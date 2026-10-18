package cn.iocoder.yudao.module.campus.identity.domain.runner;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Pure in-memory runner eligibility state machine. Persistence and locking belong to the service layer. */
public final class RunnerQualification {
    private final long qualificationId;
    private final long userId;
    private final long schoolId;
    private final long campusId;
    private final RunnerQualificationStatus status;
    private final Instant effectiveFrom;
    private final Instant expiresAt;
    private final long version;

    public RunnerQualification(long qualificationId, long userId, long schoolId, long campusId,
                               RunnerQualificationStatus status, Instant effectiveFrom,
                               Instant expiresAt, long version) {
        this.qualificationId = positive(qualificationId, "qualificationId");
        this.userId = positive(userId, "userId");
        this.schoolId = positive(schoolId, "schoolId");
        this.campusId = positive(campusId, "campusId");
        this.status = Objects.requireNonNull(status, "status");
        this.effectiveFrom = effectiveFrom;
        this.expiresAt = expiresAt;
        this.version = positive(version, "version");
        if (status != RunnerQualificationStatus.PENDING) {
            validateWindow(effectiveFrom, expiresAt);
        }
    }

    public long qualificationId() { return qualificationId; }
    public long userId() { return userId; }
    public long schoolId() { return schoolId; }
    public long campusId() { return campusId; }
    public RunnerQualificationStatus status() { return status; }
    public Instant effectiveFrom() { return effectiveFrom; }
    public Instant expiresAt() { return expiresAt; }
    public long version() { return version; }

    public RunnerQualification approve() {
        return approve(effectiveFrom, expiresAt);
    }

    public RunnerQualification approve(Instant from, Instant until) {
        requireStatus(RunnerQualificationStatus.PENDING);
        validateWindow(from, until);
        return copy(RunnerQualificationStatus.ACTIVE, from, until);
    }

    public RunnerQualification suspend() {
        requireStatus(RunnerQualificationStatus.ACTIVE);
        return copy(RunnerQualificationStatus.SUSPENDED, effectiveFrom, expiresAt);
    }

    public RunnerQualification restore(Clock clock) {
        return restore(Objects.requireNonNull(clock, "clock").instant());
    }

    public RunnerQualification restore(Instant now) {
        requireStatus(RunnerQualificationStatus.SUSPENDED);
        if (now == null || !now.isBefore(expiresAt)) {
            throw new RunnerQualificationAssertionException(RunnerQualificationAssertionException.Reason.EXPIRED);
        }
        return copy(RunnerQualificationStatus.ACTIVE, effectiveFrom, expiresAt);
    }

    public RunnerQualification revoke() {
        if (status != RunnerQualificationStatus.ACTIVE && status != RunnerQualificationStatus.SUSPENDED) {
            throw new IllegalStateException("invalid qualification transition from " + status);
        }
        return copy(RunnerQualificationStatus.REVOKED, effectiveFrom, expiresAt);
    }

    public RunnerQualificationAcceptance assertForAccept(long requestedUserId, long requestedSchoolId,
                                                          long requestedCampusId, Instant now) {
        Objects.requireNonNull(now, "now");
        if (requestedUserId != userId) fail(RunnerQualificationAssertionException.Reason.USER_SCOPE_DENIED);
        if (requestedSchoolId != schoolId) fail(RunnerQualificationAssertionException.Reason.SCHOOL_SCOPE_DENIED);
        if (requestedCampusId != campusId) fail(RunnerQualificationAssertionException.Reason.CAMPUS_SCOPE_DENIED);
        switch (status) {
            case PENDING -> fail(RunnerQualificationAssertionException.Reason.NOT_APPROVED);
            case SUSPENDED -> fail(RunnerQualificationAssertionException.Reason.SUSPENDED);
            case REVOKED -> fail(RunnerQualificationAssertionException.Reason.REVOKED);
            case ACTIVE -> { }
        }
        if (now.isBefore(effectiveFrom)) fail(RunnerQualificationAssertionException.Reason.NOT_YET_EFFECTIVE);
        if (!now.isBefore(expiresAt)) fail(RunnerQualificationAssertionException.Reason.EXPIRED);
        return new RunnerQualificationAcceptance(qualificationId, version);
    }

    public RunnerQualificationAcceptance assertForAccept(long requestedUserId, long requestedSchoolId,
                                                          long requestedCampusId, Clock clock) {
        return assertForAccept(requestedUserId, requestedSchoolId, requestedCampusId,
                Objects.requireNonNull(clock, "clock").instant());
    }

    private RunnerQualification copy(RunnerQualificationStatus next, Instant from, Instant until) {
        return new RunnerQualification(qualificationId, userId, schoolId, campusId, next, from, until, version + 1);
    }

    private void requireStatus(RunnerQualificationStatus expected) {
        if (status != expected) throw new IllegalStateException("invalid qualification transition from " + status);
    }

    private static void validateWindow(Instant from, Instant until) {
        if (from == null || until == null || !until.isAfter(from)) {
            throw new IllegalArgumentException("qualification expiry must be after effective time");
        }
    }

    private static long positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static void fail(RunnerQualificationAssertionException.Reason reason) {
        throw new RunnerQualificationAssertionException(reason);
    }
}
