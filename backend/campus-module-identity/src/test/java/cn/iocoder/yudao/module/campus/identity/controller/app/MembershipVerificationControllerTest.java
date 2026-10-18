package cn.iocoder.yudao.module.campus.identity.controller.app;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.campus.common.api.CampusExceptionHandler;
import cn.iocoder.yudao.module.campus.identity.api.MembershipCheckResult;
import cn.iocoder.yudao.module.campus.identity.application.MembershipService;
import cn.iocoder.yudao.module.campus.identity.application.VerificationApplicationService;
import cn.iocoder.yudao.module.campus.identity.application.verification.SubmitVerificationCommand;
import cn.iocoder.yudao.module.campus.identity.application.verification.VerificationApplicationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MembershipVerificationControllerTest {

    private MembershipService membershipService;
    private VerificationApplicationService verificationApplicationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        membershipService = mock(MembershipService.class);
        verificationApplicationService = mock(VerificationApplicationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MembershipVerificationController(membershipService,
                        verificationApplicationService))
                .setControllerAdvice(new CampusExceptionHandler()).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitRequiresAuthenticatedUserAndIdempotencyKeyBeforeCallingService() throws Exception {
        String body = "{\"schoolId\":\"1\",\"campusId\":\"11\",\"method\":\"STUDENT_CARD\","
                + "\"evidenceRefs\":[\"media-1\"]}";

        mockMvc.perform(post("/campus/v1/verifications").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1040000030));
        verify(verificationApplicationService, never()).submit(anyLong(), any());

        authenticate(101L);
        mockMvc.perform(post("/campus/v1/verifications").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("Idempotency-Key"));
        verify(verificationApplicationService, never()).submit(anyLong(), any());
    }

    @Test
    void submitUsesOpaqueStringIdsAndDoesNotAcceptRawEvidenceUrls() throws Exception {
        authenticate(101L);
        String request = "{\"schoolId\":\"1\",\"campusId\":\"11\",\"method\":\"STUDENT_CARD\","
                + "\"evidenceRefs\":[\"media-1\"]}";
        when(verificationApplicationService.submit(eq(101L), any(SubmitVerificationCommand.class)))
                .thenReturn(new VerificationApplicationResult("9007199254740993", "PENDING", 1));

        mockMvc.perform(post("/campus/v1/verifications").header("Idempotency-Key", "submit-1")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value("9007199254740993"))
                .andExpect(jsonPath("$.data.applicationId").isString())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        verify(verificationApplicationService).submit(eq(101L),
                eq(new SubmitVerificationCommand(1L, 11L, "STUDENT_CARD", java.util.List.of("media-1"), "submit-1")));

        mockMvc.perform(post("/campus/v1/verifications").header("Idempotency-Key", "submit-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("media-1", "https://private.example/card")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1040000000));

        mockMvc.perform(post("/campus/v1/verifications").header("Idempotency-Key", "submit-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("\"schoolId\":\"1\"", "\"schoolId\":1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1040000000));
    }

    @Test
    void membershipIgnoresClientActionAndUsesOnlyServerSelfCheckAction() throws Exception {
        authenticate(101L);
        when(membershipService.check(101L, 1L, 11L, "SELF_CHECK"))
                .thenReturn(new MembershipCheckResult(true, OffsetDateTime.parse("2026-09-17T12:00:00+08:00"), 3L));

        mockMvc.perform(get("/campus/v1/membership").param("schoolId", "1").param("campusId", "11")
                        .param("action", "FORGED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effective").value(true))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-09-17T12:00:00+08:00"))
                .andExpect(jsonPath("$.data.scopeVersion").value(3));
        verify(membershipService).check(101L, 1L, 11L, "SELF_CHECK");
    }

    @Test
    void controllerUsesModuleRelativePath() {
        assertThat(MembershipVerificationController.class.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class)
                .value()).containsExactly("/campus/v1");
    }

    private static void authenticate(long userId) {
        LoginUser user = new LoginUser();
        user.setId(userId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    }
}
