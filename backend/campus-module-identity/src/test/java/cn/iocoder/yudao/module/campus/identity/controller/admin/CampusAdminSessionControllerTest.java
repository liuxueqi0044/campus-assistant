package cn.iocoder.yudao.module.campus.identity.controller.admin;

import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.module.campus.common.api.CampusExceptionHandler;
import cn.iocoder.yudao.module.campus.identity.application.CampusAdminSessionResult;
import cn.iocoder.yudao.module.campus.identity.application.CampusAdminSessionService;
import cn.iocoder.yudao.module.campus.identity.controller.CampusPlatformPrincipalGuard;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CampusAdminSessionControllerTest {

    @Test
    void loginAndRefreshUseFrozenJsonShapes() throws Exception {
        CampusAdminSessionService service = mock(CampusAdminSessionService.class);
        CampusPlatformPrincipalGuard guard = mock(CampusPlatformPrincipalGuard.class);
        SecurityProperties properties = new SecurityProperties();
        when(service.login("admin1", "safe-pass", null)).thenReturn(result());
        when(service.refresh("refresh-token")).thenReturn(result());
        MockMvc mvc = mvc(service, guard, properties);

        mvc.perform(post("/campus/v1/session/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin1\",\"password\":\"safe-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("7"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.expiresAt").isString());
        mvc.perform(post("/campus/v1/session/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("7"));
    }

    @Test
    void logoutUsesAuthenticatedOperatorAndBearerAccessToken() throws Exception {
        CampusAdminSessionService service = mock(CampusAdminSessionService.class);
        CampusPlatformPrincipalGuard guard = mock(CampusPlatformPrincipalGuard.class);
        SecurityProperties properties = new SecurityProperties();
        when(guard.requiredOperatorId()).thenReturn(7L);
        MockMvc mvc = mvc(service, guard, properties);

        mvc.perform(post("/campus/v1/session/logout").header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        verify(service).logout(7L, "access-token");
    }

    private static MockMvc mvc(CampusAdminSessionService service, CampusPlatformPrincipalGuard guard,
                               SecurityProperties properties) {
        return MockMvcBuilders.standaloneSetup(new CampusAdminSessionController(service, guard, properties))
                .setControllerAdvice(new CampusExceptionHandler()).build();
    }

    private static CampusAdminSessionResult result() {
        return new CampusAdminSessionResult(7L, "access-token", "refresh-token",
                LocalDateTime.now().plusMinutes(30));
    }
}
