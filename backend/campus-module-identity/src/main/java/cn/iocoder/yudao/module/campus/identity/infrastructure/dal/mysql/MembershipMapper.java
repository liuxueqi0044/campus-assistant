package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.MembershipDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface MembershipMapper extends BaseMapperX<MembershipDO> {

    @Select("""
            SELECT * FROM campus_membership
            WHERE user_id = #{userId} AND school_id = #{schoolId} AND deleted = b'0'
            """)
    MembershipDO selectByUserAndSchool(@Param("userId") long userId,
                                       @Param("schoolId") long schoolId);

    @Select("""
            SELECT * FROM campus_membership
            WHERE user_id = #{userId} AND school_id = #{schoolId} AND deleted = b'0'
            FOR UPDATE
            """)
    MembershipDO selectByUserAndSchoolForUpdate(@Param("userId") long userId,
                                                 @Param("schoolId") long schoolId);

    @Update("""
            UPDATE campus_membership
            SET campus_id = #{campusId}, source_verification_id = #{verificationId}, status = 'ACTIVE',
                effective_from = #{effectiveFrom}, expires_at = #{expiresAt},
                scope_version = #{nextVersion}, revoked_at = NULL, revoked_by = NULL,
                revocation_reason = NULL, updater = #{operatorId}
            WHERE id = #{membershipId} AND scope_version = #{expectedVersion} AND deleted = b'0'
            """)
    int grant(@Param("membershipId") long membershipId,
              @Param("campusId") long campusId,
              @Param("verificationId") long verificationId,
              @Param("effectiveFrom") LocalDateTime effectiveFrom,
              @Param("expiresAt") LocalDateTime expiresAt,
              @Param("expectedVersion") long expectedVersion,
              @Param("nextVersion") long nextVersion,
              @Param("operatorId") long operatorId);

    @Update("""
            UPDATE campus_membership
            SET status = 'REVOKED', scope_version = #{nextVersion}, revoked_at = #{revokedAt},
                revoked_by = #{operatorId}, revocation_reason = #{reason}, updater = #{operatorId}
            WHERE id = #{membershipId} AND source_verification_id = #{verificationId}
              AND status = 'ACTIVE' AND scope_version = #{expectedVersion} AND deleted = b'0'
            """)
    int revoke(@Param("membershipId") long membershipId,
               @Param("verificationId") long verificationId,
               @Param("expectedVersion") long expectedVersion,
               @Param("nextVersion") long nextVersion,
               @Param("operatorId") long operatorId,
               @Param("reason") String reason,
               @Param("revokedAt") LocalDateTime revokedAt);
}
