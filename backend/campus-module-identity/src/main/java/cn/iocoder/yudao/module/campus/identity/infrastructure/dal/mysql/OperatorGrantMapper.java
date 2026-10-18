package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.OperatorGrantDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OperatorGrantMapper extends BaseMapperX<OperatorGrantDO> {

    @Select("""
            SELECT * FROM campus_operator_grant
            WHERE operator_id = #{operatorId} AND status = 'ACTIVE' AND deleted = b'0'
            ORDER BY id
            """)
    List<OperatorGrantDO> selectActiveByOperator(@Param("operatorId") long operatorId);

    @Select("""
            SELECT * FROM campus_operator_grant
            WHERE operator_id = #{operatorId} AND scope_type = #{scopeType}
              AND school_id <=> #{schoolId} AND campus_id <=> #{campusId} AND deleted = b'0'
            FOR UPDATE
            """)
    OperatorGrantDO selectScopeForUpdate(@Param("operatorId") long operatorId,
                                         @Param("scopeType") String scopeType,
                                         @Param("schoolId") Long schoolId,
                                         @Param("campusId") Long campusId);

    @Select("SELECT * FROM campus_operator_grant WHERE id = #{id} AND deleted = b'0' FOR UPDATE")
    OperatorGrantDO selectByIdForUpdate(@Param("id") long id);

    @Update("""
            UPDATE campus_operator_grant
            SET actions_json = #{actionsJson}, status = 'ACTIVE', valid_from = #{validFrom},
                expires_at = #{expiresAt}, version = #{nextVersion}, granted_by = #{actorId},
                revoked_at = NULL, revoked_by = NULL, revocation_reason = NULL,
                updater = #{actorId}
            WHERE id = #{id} AND version = #{expectedVersion} AND deleted = b'0'
            """)
    int activate(@Param("id") long id,
                 @Param("actionsJson") String actionsJson,
                 @Param("validFrom") LocalDateTime validFrom,
                 @Param("expiresAt") LocalDateTime expiresAt,
                 @Param("expectedVersion") long expectedVersion,
                 @Param("nextVersion") long nextVersion,
                 @Param("actorId") long actorId);

    @Update("""
            UPDATE campus_operator_grant
            SET status = 'REVOKED', version = #{nextVersion}, revoked_at = #{revokedAt},
                revoked_by = #{actorId}, revocation_reason = #{reason}, updater = #{actorId}
            WHERE id = #{id} AND status = 'ACTIVE' AND version = #{expectedVersion} AND deleted = b'0'
            """)
    int revoke(@Param("id") long id,
               @Param("expectedVersion") long expectedVersion,
               @Param("nextVersion") long nextVersion,
               @Param("actorId") long actorId,
               @Param("reason") String reason,
               @Param("revokedAt") LocalDateTime revokedAt);
}
