package cn.iocoder.yudao.module.campus.identity.controller.admin;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.campus.common.api.CampusExceptionHandler;
import cn.iocoder.yudao.module.campus.identity.application.VerificationApplicationService;
import cn.iocoder.yudao.module.campus.identity.application.verification.ReviewDecision;
import cn.iocoder.yudao.module.campus.identity.application.verification.ReviewVerificationCommand;
import cn.iocoder.yudao.module.campus.identity.application.verification.VerificationReviewResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VerificationReviewControllerTest {

    private VerificationApplicationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(VerificationApplicationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new VerificationReviewController(service))
                .setControllerAdvice(new CampusExceptionHandler()).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reviewRejectsMissingAuthenticationAndMalformedPathId() throws Exception {
        String request = "{\"decision\":\"APPROVE\",\"reason\":\"verified\",\"expectedVersion\":1}";
        mockMvc.perform(post("/campus/v1/verifications/51/review").header("Idempotency-Key", "review-1")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        verify(service, never()).review(anyLong(), any());

        authenticate(901L);
        mockMvc.perform(post("/campus/v1/verifications/0/review").header("Idempotency-Key", "review-1")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("id"));
    }

    @Test
    void reviewUsesTypedDecisionAndSerializesIdsAsStrings() throws Exception {
        authenticate(901L);
        when(service.review(eq(901L), any(ReviewVerificationCommand.class)))
                .thenReturn(new VerificationReviewResult("9007199254740993", "APPROVED", 2,
                        "9007199254740994", 4L));
        String request = "{\"decision\":\"APPROVE\",\"reason\":\"verified\",\"expectedVersion\":1}";

        mockMvc.perform(post("/campus/v1/verifications/9007199254740993/review")
                        .header("Idempotency-Key", "review-1").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value("9007199254740993"))
                .andExpect(jsonPath("$.data.applicationId").isString())
                .andExpect(jsonPath("$.data.membershipId").value("9007199254740994"))
                .andExpect(jsonPath("$.data.membershipId").isString());
        verify(service).review(eq(901L), eq(new ReviewVerificationCommand(9007199254740993L,
                ReviewDecision.APPROVE, "verified", 1L, "review-1")));

        mockMvc.perform(post("/campus/v1/verifications/51/review").header("Idempotency-Key", "review-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("APPROVE", "FORGED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1040000000));
    }

    @Test
    void controllerUsesModuleRelativePath() {
        assertThat(VerificationReviewController.class.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class)
                .value()).containsExactly("/campus/v1");
    }

    private static void authenticate(long userId) {
        LoginUser user = new LoginUser();
        user.setId(userId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    }
}
