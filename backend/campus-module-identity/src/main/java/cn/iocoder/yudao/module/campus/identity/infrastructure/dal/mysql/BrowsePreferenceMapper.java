package cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.BrowsePreferenceDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BrowsePreferenceMapper extends BaseMapperX<BrowsePreferenceDO> {

    /**
     * The unique user key makes concurrent/repeated PUTs converge on one preference row.
     * Scope validity is independently guarded by the composite foreign key in V0002.
     */
    @Insert("""
            INSERT INTO campus_user_browse_preference (user_id, school_id, campus_id, create_time, update_time, deleted)
            VALUES (#{userId}, #{schoolId}, #{campusId}, UTC_TIMESTAMP(), UTC_TIMESTAMP(), b'0')
            ON DUPLICATE KEY UPDATE school_id = VALUES(school_id), campus_id = VALUES(campus_id),
                update_time = UTC_TIMESTAMP(), deleted = b'0'
            """)
    int upsert(@Param("userId") Long userId, @Param("schoolId") Long schoolId, @Param("campusId") Long campusId);
}
