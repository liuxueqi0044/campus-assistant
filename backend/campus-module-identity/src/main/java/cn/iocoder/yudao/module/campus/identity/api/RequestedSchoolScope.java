package cn.iocoder.yudao.module.campus.identity.api;

/** Server-owned scope requested by an internal campus operation. */
public record RequestedSchoolScope(long schoolId, Long campusId) {

    public RequestedSchoolScope {
        if (schoolId <= 0 || campusId != null && campusId <= 0) {
            throw new IllegalArgumentException("requested scope ids must be positive");
        }
    }

    public static RequestedSchoolScope school(long schoolId) {
        return new RequestedSchoolScope(schoolId, null);
    }

    public static RequestedSchoolScope campus(long schoolId, long campusId) {
        return new RequestedSchoolScope(schoolId, campusId);
    }
}
