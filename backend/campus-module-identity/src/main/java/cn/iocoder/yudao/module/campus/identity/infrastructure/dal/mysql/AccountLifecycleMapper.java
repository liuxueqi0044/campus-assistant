package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.AccountLifecycleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AccountLifecycleMapper extends BaseMapperX<AccountLifecycleDO> {

    @Select("SELECT * FROM campus_account_lifecycle WHERE user_id=#{userId} AND deleted=b'0'")
    AccountLifecycleDO selectLifecycle(@Param("userId") long userId);

    @Select("SELECT * FROM campus_account_lifecycle WHERE user_id=#{userId} AND deleted=b'0' FOR UPDATE")
    AccountLifecycleDO selectLifecycleForUpdate(@Param("userId") long userId);

    @Update("""
            UPDATE campus_account_lifecycle
            SET status=#{targetStatus}, auth_version=#{nextAuthVersion}, version=#{nextVersion},
                status_reason=#{reason}, closure_requested_at=#{closureRequestedAt}, closed_at=#{closedAt},
                session_revocation_state='PENDING', last_transition_actor_type=#{actorType},
                last_transition_by=#{actorId}, updater=#{actorId}
            WHERE user_id=#{userId} AND version=#{expectedVersion} AND status=#{sourceStatus} AND deleted=b'0'
            """)
    int transition(@Param("userId") long userId,
                   @Param("sourceStatus") String sourceStatus,
                   @Param("targetStatus") String targetStatus,
                   @Param("nextAuthVersion") long nextAuthVersion,
                   @Param("expectedVersion") long expectedVersion,
                   @Param("nextVersion") long nextVersion,
                   @Param("reason") String reason,
                   @Param("closureRequestedAt") LocalDateTime closureRequestedAt,
                   @Param("closedAt") LocalDateTime closedAt,
                   @Param("actorType") String actorType,
                   @Param("actorId") Long actorId);

    @Update("""
            UPDATE campus_account_lifecycle
            SET session_revocation_state='COMPLETE'
            WHERE user_id=#{userId} AND auth_version=#{authVersion}
              AND session_revocation_state='PENDING' AND deleted=b'0'
            """)
    int markSessionRevoked(@Param("userId") long userId, @Param("authVersion") long authVersion);
}
