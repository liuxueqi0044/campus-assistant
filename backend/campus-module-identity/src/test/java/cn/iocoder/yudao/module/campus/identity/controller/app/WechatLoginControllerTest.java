package cn.iocoder.yudao.module.campus.identity.controller.app;

import cn.iocoder.yudao.module.campus.common.api.CampusExceptionHandler;
import cn.iocoder.yudao.module.campus.identity.api.IdentityApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WechatLoginControllerTest {

    private IdentityApi identityApi;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        identityApi = mock(IdentityApi.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WechatLoginController(identityApi))
                .setControllerAdvice(new CampusExceptionHandler()).build();
    }

    @Test
    void onlyCodeIsPassedToIdentityBoundaryAndResponseExcludesChannelFields() throws Exception {
        when(identityApi.loginWithWechatCode("code-from-uni-login"))
                .thenReturn(new IdentityApi.IdentityLoginResult(7L, "member-token",
                        OffsetDateTime.parse("2026-09-17T12:00:00+08:00"), false));

        mockMvc.perform(post("/campus/v1/auth/wechat/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"code-from-uni-login\",\"clientContext\":{\"openid\":\"forged\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("7"))
                .andExpect(jsonPath("$.data.accessToken").value("member-token"))
                .andExpect(jsonPath("$.data.openid").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.sessionKey").doesNotExist());
        verify(identityApi).loginWithWechatCode("code-from-uni-login");
    }

    @Test
    void blankCodeIsRejectedBeforeAnyIdentityCall() throws Exception {
        mockMvc.perform(post("/campus/v1/auth/wechat/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1040000000));
    }

    @Test
    void controllerUsesModuleRelativePath() {
        assertThat(WechatLoginController.class.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class).value())
                .containsExactly("/campus/v1/auth/wechat");
    }
}
