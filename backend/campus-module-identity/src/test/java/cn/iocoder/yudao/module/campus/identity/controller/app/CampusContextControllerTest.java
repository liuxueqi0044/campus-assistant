package cn.iocoder.yudao.module.campus.identity.controller.app;

import cn.iocoder.yudao.module.campus.common.api.CampusExceptionHandler;
import cn.iocoder.yudao.module.campus.identity.application.CampusContextService;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.CampusContextResponse;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.CampusPublicResponse;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.SchoolPublicResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP slice proof that anonymous context stays public and anonymous PUT does not gain an identity. */
class CampusContextControllerTest {

    private CampusContextService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CampusContextService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CampusContextController(service))
                .setControllerAdvice(new CampusExceptionHandler()).build();
    }

    @Test
    void controllerKeepsOnlyModuleRelativePathBecauseYudaoAddsTheAppPrefix() {
        RequestMapping mapping = CampusContextController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/campus/v1");
    }

    @Test
    void anonymousGetContextContainsNoMembershipOrPrivateFields() throws Exception {
        when(service.getContext(isNull())).thenReturn(new CampusContextResponse(false, "PUBLIC", null, null));

        // standalone MockMvc does not install Yudao's controller.app path prefix.
        mockMvc.perform(get("/campus/v1/context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.loggedIn").value(false))
                .andExpect(jsonPath("$.data.browseMode").value("PUBLIC"))
                .andExpect(jsonPath("$.data.membership").doesNotExist())
                .andExpect(jsonPath("$.data.role").doesNotExist())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        verify(service).getContext(null);
    }

    @Test
    void anonymousPutPassesNoUserAndReturnsOnlyVerifiedPublicScope() throws Exception {
        CampusPublicResponse campus = new CampusPublicResponse("1", "11", "主校区", "Asia/Shanghai", "ENABLED", "1");
        SchoolPublicResponse school = new SchoolPublicResponse("1", "示例大学", "Asia/Shanghai", "ENABLED", "1", List.of());
        when(service.updateContext(isNull(), eq("1"), eq("11")))
                .thenReturn(new CampusContextResponse(false, "PUBLIC", school, campus));

        mockMvc.perform(put("/campus/v1/context").contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"schoolId\":\"1\",\"campusId\":\"11\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loggedIn").value(false))
                .andExpect(jsonPath("$.data.school.schoolId").value("1"))
                .andExpect(jsonPath("$.data.campus.schoolId").value("1"))
                .andExpect(jsonPath("$.data.campus.campusId").value("11"))
                .andExpect(jsonPath("$.data.membership").doesNotExist());
        verify(service).updateContext(null, "1", "11");
    }
}
