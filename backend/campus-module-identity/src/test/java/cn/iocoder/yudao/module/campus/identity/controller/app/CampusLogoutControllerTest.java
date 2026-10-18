package cn.iocoder.yudao.module.campus.identity.controller.app;

import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.module.campus.common.api.CampusExceptionHandler;
import cn.iocoder.yudao.module.campus.identity.api.IdentityApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CampusLogoutControllerTest {

    private IdentityApi identityApi;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        identityApi = mock(IdentityApi.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CampusLogoutController(identityApi, new SecurityProperties()))
                .setControllerAdvice(new CampusExceptionHandler()).build();
    }

    @Test
    void delegatesBearerRevocationAndReturnsCampusSuccessEnvelope() throws Exception {
        mockMvc.perform(post("/campus/v1/auth/logout").header("Authorization", "Bearer member-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        verify(identityApi).logout("member-token");
    }

    @Test
    void absentTokenRemainsIdempotentSuccess() throws Exception {
        mockMvc.perform(post("/campus/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        verify(identityApi, never()).logout(org.mockito.ArgumentMatchers.any());
    }
}
