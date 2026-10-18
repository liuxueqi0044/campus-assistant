package cn.iocoder.yudao.module.campus.identity.api;

/** Typed exact query restriction compiled from an authorization proof, never from request headers. */
public record AuthorizedSchoolQuery(long schoolId, Long campusId,
                                    long grantId, long authorizationVersion) {
}
