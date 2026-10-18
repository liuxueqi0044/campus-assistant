package cn.iocoder.yudao.module.campus.common.api;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M00.02 验收（A 级）：traceId 始终存在，非法入站值必须被替换，线程复用不留残余。
 */
class CampusTraceContextTest {

    private final CampusTraceFilter filter = new CampusTraceFilter();

    @AfterEach
    void tearDown() {
        CampusTraceContext.clear();
    }

    @Test
    void bind_shouldAcceptTrustedServerValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(CampusTraceContext.bind(request, "trace-from-gateway-0001"))
                .isEqualTo("trace-from-gateway-0001");
    }

    @Test
    void bind_shouldReplaceIllegalInboundValueToAvoidLogInjection() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(CampusTraceContext.bind(request, "bad trace!\nroom")).matches("[0-9a-f]{32}");
    }

    @Test
    void currentOrNewTraceId_shouldAlwaysReturnNonBlankValue() {
        assertThat(CampusTraceContext.currentOrNewTraceId()).isNotBlank();
    }

    @Test
    void filter_shouldEchoTraceIdOnResponseHeaderAndClearMdcAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(CampusApiContract.APP_API_PREFIX + "/orders");
        request.addHeader(CampusApiContract.HEADER_TRACE_ID, "untrusted-client-trace-0002");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get(CampusTraceContext.MDC_KEY)).matches("[0-9a-f]{32}");
            assertThat(MDC.get(CampusTraceContext.MDC_KEY)).isNotEqualTo("untrusted-client-trace-0002");
        });

        assertThat(response.getHeader(CampusApiContract.HEADER_TRACE_ID)).matches("[0-9a-f]{32}");
        assertThat(MDC.get(CampusTraceContext.MDC_KEY)).isNull();
    }

    @Test
    void filter_shouldGenerateTraceIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(CampusApiContract.ADMIN_API_PREFIX + "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (FilterChain) (req, res) -> {
        });

        assertThat(response.getHeader(CampusApiContract.HEADER_TRACE_ID)).matches("[0-9a-f]{32}");
    }

    @Test
    void filter_shouldNotChangeNonCampusResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin-api/system/auth/get-permission-info");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (FilterChain) (req, res) -> {
            assertThat(MDC.get(CampusTraceContext.MDC_KEY)).isNull();
        });

        assertThat(response.getHeader(CampusApiContract.HEADER_TRACE_ID)).isNull();
    }

}
