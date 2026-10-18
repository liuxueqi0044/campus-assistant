package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.module.campus.identity.domain.CampusStatus;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.BrowsePreferenceDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.CampusDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.SchoolDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql.BrowsePreferenceMapper;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql.CampusMapper;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.mysql.SchoolMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** MyBatis-Plus persistence adapter for the M01.02-owned tables. */
@Repository
@RequiredArgsConstructor
public class SchoolRepository {

    private final SchoolMapper schoolMapper;
    private final CampusMapper campusMapper;
    private final BrowsePreferenceMapper browsePreferenceMapper;

    public SchoolDO findSchool(Long schoolId) {
        return schoolMapper.selectById(schoolId);
    }

    public CampusDO findCampus(Long campusId) {
        return campusMapper.selectById(campusId);
    }

    public BrowsePreferenceDO findBrowsePreference(Long userId) {
        return browsePreferenceMapper.selectOne(BrowsePreferenceDO::getUserId, userId);
    }

    public void upsertBrowsePreference(Long userId, Long schoolId, Long campusId) {
        browsePreferenceMapper.upsert(userId, schoolId, campusId);
    }

    public List<SchoolDO> findEnabledSchoolsAfter(int sortOrder, long id, String keyword, int limit) {
        return schoolMapper.selectList(new LambdaQueryWrapper<SchoolDO>()
                .eq(SchoolDO::getStatus, CampusStatus.ENABLED)
                .like(keyword != null && !keyword.isBlank(), SchoolDO::getName, keyword)
                .and(id > 0, wrapper -> wrapper.gt(SchoolDO::getSortOrder, sortOrder)
                        .or().eq(SchoolDO::getSortOrder, sortOrder).gt(SchoolDO::getId, id))
                .exists("SELECT 1 FROM campus_campus c WHERE c.school_id = campus_school.id "
                        + "AND c.status = 'ENABLED' AND c.deleted = b'0'")
                .orderByAsc(SchoolDO::getSortOrder, SchoolDO::getId)
                .last("LIMIT " + limit));
    }

    public List<CampusDO> findEnabledCampuses(Long schoolId) {
        return campusMapper.selectList(new LambdaQueryWrapper<CampusDO>()
                .eq(CampusDO::getSchoolId, schoolId)
                .eq(CampusDO::getStatus, CampusStatus.ENABLED)
                .orderByAsc(CampusDO::getSortOrder, CampusDO::getId));
    }
}
