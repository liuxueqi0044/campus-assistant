package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.CampusContextResponse;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.SchoolDirectoryResponse;
import cn.iocoder.yudao.module.campus.identity.infrastructure.SchoolRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.CampusDO;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.SchoolDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A-level application-rule evidence; membership is intentionally not a collaborator of this service. */
@ExtendWith(MockitoExtension.class)
class CampusContextServiceTest {

    @Mock
    private SchoolRepository schoolRepository;

    private CampusContextService service;

    @BeforeEach
    void setUp() {
        service = new CampusContextService(schoolRepository);
    }

    @Test
    void mismatchedSchoolAndCampusIsRejectedBeforePreferenceWrite() {
        when(schoolRepository.findSchool(1L)).thenReturn(school(1L, "S1", "ENABLED"));
        when(schoolRepository.findCampus(21L)).thenReturn(campus(21L, 2L, "C21", "ENABLED"));

        assertThatThrownBy(() -> service.updateContext(101L, "1", "21"))
                .isInstanceOf(CampusApiException.class)
                .extracting(exception -> ((CampusApiException) exception).getSpec())
                .isEqualTo(CampusErrorCodes.CAMPUS_SCHOOL_MISMATCH);

        verify(schoolRepository, never()).upsertBrowsePreference(anyLong(), anyLong(), anyLong());
    }

    @Test
    void inactiveCampusIsRejectedBeforePreferenceWrite() {
        when(schoolRepository.findSchool(1L)).thenReturn(school(1L, "S1", "ENABLED"));
        when(schoolRepository.findCampus(11L)).thenReturn(campus(11L, 1L, "C11", "DISABLED"));

        assertThatThrownBy(() -> service.updateContext(101L, "1", "11"))
                .isInstanceOf(CampusApiException.class)
                .extracting(exception -> ((CampusApiException) exception).getSpec())
                .isEqualTo(CampusErrorCodes.CAMPUS_UNAVAILABLE);

        verify(schoolRepository, never()).upsertBrowsePreference(anyLong(), anyLong(), anyLong());
    }

    @Test
    void anonymousPutVerifiesScopeButNeverPersistsPreference() {
        configureScope(1L, 11L);

        CampusContextResponse response = service.updateContext(null, "1", "11");

        assertThat(response.isLoggedIn()).isFalse();
        assertThat(response.getBrowseMode()).isEqualTo("PUBLIC");
        assertThat(response.getSchool().getSchoolId()).isEqualTo("1");
        assertThat(response.getCampus().getSchoolId()).isEqualTo("1");
        assertThat(response.getCampus().getCampusId()).isEqualTo("11");
        verify(schoolRepository, never()).upsertBrowsePreference(anyLong(), anyLong(), anyLong());
    }

    @Test
    void loggedInPutOnlyWritesIndependentBrowsePreferenceAndReturnsPublicFields() {
        configureScope(2L, 21L);

        CampusContextResponse response = service.updateContext(101L, "2", "21");

        verify(schoolRepository).upsertBrowsePreference(101L, 2L, 21L);
        assertThat(response.isLoggedIn()).isTrue();
        assertThat(response.getSchool().getCampuses()).isEmpty();
        assertThat(response.getClass().getDeclaredFields()).extracting("name")
                .containsExactlyInAnyOrder("loggedIn", "browseMode", "school", "campus");
    }

    @Test
    void directoryOnlyContainsEnabledSchoolAndCampusDtosAndUsesNextCursor() {
        when(schoolRepository.findEnabledSchoolsAfter(anyInt(), anyLong(), any(), anyInt())).thenReturn(List.of(
                school(1L, "S1", "ENABLED"), school(2L, "S2", "ENABLED")));
        when(schoolRepository.findEnabledCampuses(1L)).thenReturn(List.of(campus(11L, 1L, "C11", "ENABLED")));
        when(schoolRepository.findEnabledCampuses(2L)).thenReturn(List.of(campus(21L, 2L, "C21", "ENABLED")));

        SchoolDirectoryResponse response = service.listSchools("大学", null);

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems()).allSatisfy(school -> {
            assertThat(school.getStatus()).isEqualTo("ENABLED");
            assertThat(school.getCampuses()).allSatisfy(campus -> assertThat(campus.getStatus()).isEqualTo("ENABLED"));
        });
        assertThat(response.getNextCursor()).isNull();
    }

    private void configureScope(Long schoolId, Long campusId) {
        SchoolDO school = school(schoolId, "S" + schoolId, "ENABLED");
        CampusDO campus = campus(campusId, schoolId, "C" + campusId, "ENABLED");
        when(schoolRepository.findSchool(schoolId)).thenReturn(school);
        when(schoolRepository.findCampus(campusId)).thenReturn(campus);
    }

    private static SchoolDO school(Long id, String name, String status) {
        SchoolDO row = new SchoolDO();
        row.setId(id);
        row.setName(name);
        row.setTimezone("Asia/Shanghai");
        row.setStatus(status);
        row.setConfigVersion(1L);
        row.setSortOrder(0);
        return row;
    }

    private static CampusDO campus(Long id, Long schoolId, String name, String status) {
        CampusDO row = new CampusDO();
        row.setId(id);
        row.setSchoolId(schoolId);
        row.setName(name);
        row.setTimezone("Asia/Shanghai");
        row.setStatus(status);
        row.setConfigVersion(1L);
        row.setSortOrder(0);
        return row;
    }
}
