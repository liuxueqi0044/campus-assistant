package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.VerificationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface VerificationMapper extends BaseMapperX<VerificationDO> {

    @Select("SELECT * FROM campus_verification WHERE id = #{id} AND deleted = b'0'")
    VerificationDO selectApplication(@Param("id") long id);

    @Select("SELECT * FROM campus_verification WHERE id = #{id} AND deleted = b'0' FOR UPDATE")
    VerificationDO selectByIdForUpdate(@Param("id") long id);

    @Update("""
            UPDATE campus_verification
            SET status = #{targetStatus}, version = version + 1,
                reviewed_at = #{reviewedAt}, reviewed_by = #{operatorId}, review_reason = #{reason},
                valid_from = #{validFrom}, expires_at = #{expiresAt}, updater = #{operatorId}
            WHERE id = #{id} AND status = #{expectedStatus} AND version = #{expectedVersion}
              AND deleted = b'0'
            """)
    int transition(@Param("id") long id,
                   @Param("expectedStatus") String expectedStatus,
                   @Param("targetStatus") String targetStatus,
                   @Param("expectedVersion") long expectedVersion,
                   @Param("operatorId") long operatorId,
                   @Param("reason") String reason,
                   @Param("reviewedAt") LocalDateTime reviewedAt,
                   @Param("validFrom") LocalDateTime validFrom,
                   @Param("expiresAt") LocalDateTime expiresAt);
}
