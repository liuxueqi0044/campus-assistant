package cn.iocoder.yudao.module.campus.identity.framework.tenant;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CampusFixedTenantWebFilterTest {

    private static final long PLATFORM_TENANT_ID = 1L;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void pinsMatchingCampusPathAndRestoresPreviousTenant() throws Exception {
        TenantContextHolder.setTenantId(77L);
        FilterChain chain = mock(FilterChain.class);
        AtomicReference<Long> observedTenant = new AtomicReference<>();
        doAnswer(invocation -> {
            observedTenant.set(TenantContextHolder.getTenantId());
            return null;
        }).when(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        CampusFixedTenantWebFilter filter = new CampusFixedTenantWebFilter(PLATFORM_TENANT_ID);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin-api/campus/v1/session/login");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(eq(request), org.mockito.ArgumentMatchers.any());
        // The chain observes the fixed tenant before the filter restores the caller's context.
        assertThat(observedTenant.get()).isEqualTo(PLATFORM_TENANT_ID);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(77L);
    }

    @Test
    void clearsContextWhenMatchingRequestStartsWithoutTenant() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        CampusFixedTenantWebFilter filter = new CampusFixedTenantWebFilter(PLATFORM_TENANT_ID);

        filter.doFilter(new MockHttpServletRequest("GET", "/admin-api/campus/v1/verifications/123"),
                new MockHttpServletResponse(), chain);

        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void leavesNonCampusPathUntouched() throws Exception {
        TenantContextHolder.setTenantId(77L);
        FilterChain chain = mock(FilterChain.class);
        CampusFixedTenantWebFilter filter = new CampusFixedTenantWebFilter(PLATFORM_TENANT_ID);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-api/system/auth/get-permission-info");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(77L);
        verify(chain).doFilter(eq(request), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void leavesStudentAndHealthPathsUntouched() throws Exception {
        CampusFixedTenantWebFilter filter = new CampusFixedTenantWebFilter(PLATFORM_TENANT_ID);
        FilterChain chain = mock(FilterChain.class);

        TenantContextHolder.setTenantId(77L);
        MockHttpServletRequest studentRequest = new MockHttpServletRequest("GET", "/app-api/campus/v1/context");
        filter.doFilter(studentRequest, new MockHttpServletResponse(), chain);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(77L);

        MockHttpServletRequest healthRequest = new MockHttpServletRequest("GET", "/health");
        filter.doFilter(healthRequest, new MockHttpServletResponse(), chain);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(77L);

        MockHttpServletRequest reviewRequest = new MockHttpServletRequest("POST",
                "/admin-api/campus/v1/verifications/123/review");
        filter.doFilter(reviewRequest, new MockHttpServletResponse(), chain);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(77L);
    }
}
