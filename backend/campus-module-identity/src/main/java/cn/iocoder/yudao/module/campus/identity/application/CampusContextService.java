package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorDetail;
import cn.iocoder.yudao.module.campus.common.api.CampusFieldError;
import cn.iocoder.yudao.module.campus.identity.api.CampusPublicScope;
import cn.iocoder.yudao.module.campus.identity.api.CampusScopeApi;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.CampusContextResponse;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.CampusPublicResponse;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.SchoolDirectoryResponse;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.SchoolPublicResponse;
import cn.iocoder.yudao.module.campus.identity.domain.CampusStatus;
import cn.iocoder.yudao.module.campus.identity.infrastructure.SchoolRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.BrowsePreferenceDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.CampusDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.SchoolDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Application boundary for M01.02; it has no dependency on membership or tenant request headers. */
@Service
@RequiredArgsConstructor
public class CampusContextService implements CampusScopeApi {

    private static final int DIRECTORY_PAGE_SIZE = 20;
    private static final String PUBLIC_BROWSE_MODE = "PUBLIC";

    private final SchoolRepository schoolRepository;

    public SchoolDirectoryResponse listSchools(String keyword, String rawCursor) {
        CampusDirectoryCursor cursor = CampusDirectoryCursor.decode(rawCursor);
        List<SchoolDO> fetched = schoolRepository.findEnabledSchoolsAfter(cursor.sortOrder(), cursor.schoolId(), keyword,
                DIRECTORY_PAGE_SIZE + 1);
        boolean hasNext = fetched.size() > DIRECTORY_PAGE_SIZE;
        List<SchoolDO> page = hasNext ? fetched.subList(0, DIRECTORY_PAGE_SIZE) : fetched;
        List<SchoolPublicResponse> items = page.stream().map(this::toSchoolResponse).toList();
        String nextCursor = null;
        if (hasNext) {
            SchoolDO last = page.get(page.size() - 1);
            nextCursor = CampusDirectoryCursor.encode(last.getSortOrder(), last.getId());
        }
        return new SchoolDirectoryResponse(items, nextCursor);
    }

    public CampusContextResponse getContext(Long userId) {
        if (userId == null) {
            return new CampusContextResponse(false, PUBLIC_BROWSE_MODE, null, null);
        }
        BrowsePreferenceDO preference = schoolRepository.findBrowsePreference(userId);
        if (preference == null) {
            return new CampusContextResponse(true, PUBLIC_BROWSE_MODE, null, null);
        }
        CampusPublicScope scope = resolvePublicScope(preference.getSchoolId(), preference.getCampusId());
        return contextFor(true, scope);
    }

    /** Anonymous calls verify the requested scope but cannot persist a browser preference. */
    @Transactional(rollbackFor = Exception.class)
    public CampusContextResponse updateContext(Long userId, String rawSchoolId, String rawCampusId) {
        CampusPublicScope scope = resolvePublicScope(parseId(rawSchoolId, "schoolId"), parseId(rawCampusId, "campusId"));
        if (userId != null) {
            schoolRepository.upsertBrowsePreference(userId, scope.schoolId(), scope.campusId());
        }
        return contextFor(userId != null, scope);
    }

    @Override
    public void assertCampusBelongsToSchool(Long schoolId, Long campusId) {
        resolvePublicScope(schoolId, campusId);
    }

    @Override
    public CampusPublicScope resolvePublicScope(Long schoolId, Long campusId) {
        SchoolDO school = schoolId == null ? null : schoolRepository.findSchool(schoolId);
        CampusDO campus = campusId == null ? null : schoolRepository.findCampus(campusId);
        if (school == null || campus == null || !CampusStatus.ENABLED.equals(school.getStatus())
                || !CampusStatus.ENABLED.equals(campus.getStatus())) {
            throw new CampusApiException(CampusErrorCodes.CAMPUS_UNAVAILABLE);
        }
        if (!school.getId().equals(campus.getSchoolId())) {
            throw new CampusApiException(CampusErrorCodes.CAMPUS_SCHOOL_MISMATCH);
        }
        return new CampusPublicScope(school.getId(), campus.getId(), school.getConfigVersion(), campus.getConfigVersion());
    }

    private CampusContextResponse contextFor(boolean loggedIn, CampusPublicScope scope) {
        SchoolDO school = schoolRepository.findSchool(scope.schoolId());
        CampusDO campus = schoolRepository.findCampus(scope.campusId());
        // resolvePublicScope already read authoritative rows; reread here only creates public DTOs and cannot grant access.
        return new CampusContextResponse(loggedIn, PUBLIC_BROWSE_MODE, toSchoolWithoutCampuses(school), toCampusResponse(campus));
    }

    private SchoolPublicResponse toSchoolResponse(SchoolDO school) {
        List<CampusPublicResponse> campuses = schoolRepository.findEnabledCampuses(school.getId()).stream()
                .map(this::toCampusResponse).toList();
        return new SchoolPublicResponse(String.valueOf(school.getId()), school.getName(), school.getTimezone(),
                school.getStatus(), String.valueOf(school.getConfigVersion()), campuses);
    }

    private SchoolPublicResponse toSchoolWithoutCampuses(SchoolDO school) {
        return new SchoolPublicResponse(String.valueOf(school.getId()), school.getName(), school.getTimezone(),
                school.getStatus(), String.valueOf(school.getConfigVersion()), List.of());
    }

    private CampusPublicResponse toCampusResponse(CampusDO campus) {
        return new CampusPublicResponse(String.valueOf(campus.getSchoolId()), String.valueOf(campus.getId()),
                campus.getName(), campus.getTimezone(),
                campus.getStatus(), String.valueOf(campus.getConfigVersion()));
    }

    private static Long parseId(String raw, String field) {
        try {
            if (raw == null || !raw.matches("[1-9][0-9]*")) {
                throw new NumberFormatException("non-positive id");
            }
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID,
                    CampusErrorDetail.of(CampusErrorCodes.REQUEST_INVALID.getRetryAction())
                            .withFieldErrors(List.of(new CampusFieldError(field, "标识字段必须是正整数字符串"))));
        }
    }
}
