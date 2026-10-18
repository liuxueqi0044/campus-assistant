package cn.iocoder.yudao.module.campus.common.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M00.02 验收（A 级）：统一响应、字符串 ID、整数分金额、分页上限、409 冲突与兜底 500。
 *
 * <p>断言基于真实 HTTP 端点（探针控制器）与真实 {@link CampusExceptionHandler}，
 * 不使用内存库或 mock 异常映射。
 */
class CampusApiContractTest {

    private static final String PROBE = CampusApiContract.APP_API_PREFIX + "/contract-probe";

    /** 超过 JavaScript 安全整数上限（2^53-1）的编号 */
    private static final String BIG_ID = "9007199254740993";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CampusContractProbeController())
                .setControllerAdvice(new CampusExceptionHandler(), new CampusUnmappedPathExceptionHandler())
                .addFilters(new CampusTraceFilter())
                // 与生产一致：未匹配地址抛出 NoHandlerFoundException，由统一映射给出 404 契约响应
                .addDispatcherServletCustomizer(dispatcherServlet -> dispatcherServlet
                        .setThrowExceptionIfNoHandlerFound(true))
                .build();
    }

    @Test
    void successResponse_shouldUseCampusEnvelopeWithStringIdAndIntegerMoney() throws Exception {
        mockMvc.perform(get(PROBE + "/orders/{id}", BIG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value(""))
                // 超过安全整数的大编号必须是字符串，否则前端会丢精度
                .andExpect(jsonPath("$.data.id").value(BIG_ID))
                // 金额是整数分，不是元、也不是字符串
                .andExpect(jsonPath("$.data.amountFen").value(1234))
                // 时间带时区偏移
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-17T10:15:30+08:00"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists(CampusApiContract.HEADER_TRACE_ID));
    }

    @Test
    void successResponse_shouldUseStringIdEvenWhenSafeInteger() throws Exception {
        mockMvc.perform(get(PROBE + "/orders/{id}", 42))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("42"));
    }

    @Test
    void untrustedInboundTraceId_shouldBeReplacedInBodyAndResponseHeader() throws Exception {
        MvcResult result = mockMvc.perform(get(PROBE + "/orders/{id}", 1)
                        .header(CampusApiContract.HEADER_TRACE_ID, "untrusted-client-trace-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists(CampusApiContract.HEADER_TRACE_ID))
                .andReturn();

        String traceId = result.getResponse().getHeader(CampusApiContract.HEADER_TRACE_ID);
        assertThat(traceId).matches("[0-9a-f]{32}").isNotEqualTo("untrusted-client-trace-0001");
        assertThat(result.getResponse().getContentAsString()).contains("\"traceId\":\"" + traceId + "\"");
    }

    @Test
    void createOrder_shouldRoundTripStringIdAndIntegerMoney() throws Exception {
        mockMvc.perform(post(PROBE + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buyerId\":\"" + BIG_ID + "\",\"schoolId\":\"S1\",\"amountFen\":1234}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(BIG_ID))
                .andExpect(jsonPath("$.data.amountFen").value(1234));
    }

    @Test
    void numericIdInJson_shouldBeRejectedInsteadOfCoerced() throws Exception {
        mockMvc.perform(post(PROBE + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buyerId\":9007199254740993,\"schoolId\":\"S1\",\"amountFen\":1234}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CampusErrorCodes.REQUEST_INVALID.getCode()))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("buyerId"));
    }

    @Test
    void decimalMoney_shouldReturnFrozenBadRequestWithoutLeakingInternals() throws Exception {
        MvcResult result = mockMvc.perform(post(PROBE + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buyerId\":\"1\",\"schoolId\":\"S1\",\"amountFen\":12.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CampusErrorCodes.MONEY_FORMAT_INVALID.getCode()))
                .andExpect(jsonPath("$.data.requiredAction").value("USER_FIX"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception").doesNotContain("JsonMapping").doesNotContain("\tat ");
    }

    @Test
    void stringMoney_shouldAlsoBeRejected() throws Exception {
        mockMvc.perform(post(PROBE + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buyerId\":\"1\",\"schoolId\":\"S1\",\"amountFen\":\"1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CampusErrorCodes.MONEY_FORMAT_INVALID.getCode()));
    }

    @Test
    void missingMoney_shouldReturnBadRequestWithFieldError() throws Exception {
        mockMvc.perform(post(PROBE + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buyerId\":\"1\",\"schoolId\":\"S1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CampusErrorCodes.REQUEST_INVALID.getCode()))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("amountFen"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void pageSizeAboveCap_shouldReturnFrozenBadRequest() throws Exception {
        mockMvc.perform(get(PROBE + "/orders").param("pageSize",
                        String.valueOf(CampusPageParam.MAX_PAGE_SIZE + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CampusErrorCodes.PAGE_SIZE_EXCEEDED.getCode()))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("pageSize"));
    }

    @Test
    void pageSizeAbsent_shouldUseContractDefaults() throws Exception {
        mockMvc.perform(get(PROBE + "/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNo").value(CampusPageParam.DEFAULT_PAGE_NO))
                .andExpect(jsonPath("$.data.pageSize").value(CampusPageParam.DEFAULT_PAGE_SIZE));
    }

    @Test
    void versionConflict_shouldReturn409WithCurrentVersionAndState() throws Exception {
        mockMvc.perform(get(PROBE + "/version-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(CampusErrorCodes.VERSION_CONFLICT.getCode()))
                .andExpect(jsonPath("$.data.currentVersion").value("7"))
                .andExpect(jsonPath("$.data.currentState").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.retryable").value(false))
                .andExpect(jsonPath("$.data.requiredAction").value("USER_FIX"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void retryableTransactionConflict_shouldReturnSanitized409() throws Exception {
        MvcResult result = mockMvc.perform(get(PROBE + "/retryable-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(CampusErrorCodes.RETRYABLE_CONFLICT.getCode()))
                .andExpect(jsonPath("$.data.retryable").value(true))
                .andExpect(jsonPath("$.data.requiredAction").value("RETRY"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("internal-operation")
                .doesNotContain("database detail");
    }

    @Test
    void unexpectedException_shouldReturnSanitized500WithTraceId() throws Exception {
        MvcResult result = mockMvc.perform(get(PROBE + "/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(CampusErrorCodes.INTERNAL_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("系统异常"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("jdbc:mysql")
                .doesNotContain("IllegalStateException")
                .doesNotContain("\tat ")
                .doesNotContain(CampusContractProbeController.class.getName());
    }

    @Test
    void timestampInBody_shouldRoundTripAsOffsetString() throws Exception {
        mockMvc.perform(post(PROBE + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buyerId\":\"1\",\"schoolId\":\"S1\",\"amountFen\":1234,"
                                + "\"expectedAt\":\"2026-09-17T10:15:30+08:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-17T10:15:30+08:00"));
    }

    @Test
    void timestampAsEpochNumber_shouldBeRejected() throws Exception {
        mockMvc.perform(post(PROBE + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buyerId\":\"1\",\"schoolId\":\"S1\",\"amountFen\":1234,"
                                + "\"expectedAt\":1789611330}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CampusErrorCodes.REQUEST_INVALID.getCode()))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("expectedAt"));
    }

    @Test
    void hiddenResource_shouldReturn404WithoutRevealingExistence() throws Exception {
        MvcResult result = mockMvc.perform(get(PROBE + "/hidden-orders/{id}", 123))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(CampusErrorCodes.RESOURCE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("资源不存在"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();

        // 不泄漏存在性：响应里不出现被访问的资源路径，也不给出版本或状态
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("hidden-orders")
                .doesNotContain("currentVersion");
    }

    @Test
    void unknownPath_shouldReturnNotFoundWithCampusEnvelope() throws Exception {
        mockMvc.perform(get(PROBE + "/not-existing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(CampusErrorCodes.RESOURCE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

}
