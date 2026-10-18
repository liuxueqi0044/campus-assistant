package cn.iocoder.yudao.module.campus.common.api.security;

import cn.iocoder.yudao.framework.security.core.handler.AccessDeniedHandlerImpl;
import cn.iocoder.yudao.framework.security.core.handler.AuthenticationEntryPointImpl;
import cn.iocoder.yudao.module.campus.common.api.CampusApiContract;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.common.api.CampusTraceFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用真实 Spring Security FilterChain 验证 MVC advice 之前的 401/403 边界。 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CampusSecurityFilterChainTest.TestConfiguration.class)
@WebAppConfiguration
class CampusSecurityFilterChainTest {

    private static final String CAMPUS_PATH = CampusApiContract.APP_API_PREFIX + "/secured";
    private static final String CAMPUS_FORBIDDEN_PATH = CampusApiContract.ADMIN_API_PREFIX + "/forbidden";
    private static final String UPSTREAM_PATH = "/admin-api/system/secured";

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DelegatingFilterProxy security = new DelegatingFilterProxy("springSecurityFilterChain", applicationContext);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(new CampusTraceFilter(), security)
                .build();
    }

    @Test
    void unauthenticatedCampusPath_shouldReturnHttp401AndCampusEnvelope() throws Exception {
        mockMvc.perform(get(CAMPUS_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CampusErrorCodes.AUTH_REQUIRED.getCode()))
                .andExpect(jsonPath("$.message").value("账号未登录"))
                .andExpect(jsonPath("$.data.requiredAction").value("USER_FIX"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists(CampusApiContract.HEADER_TRACE_ID));
    }

    @Test
    void authenticatedCampusPathWithoutAuthority_shouldReturnHttp403AndCampusEnvelope() throws Exception {
        mockMvc.perform(get(CAMPUS_FORBIDDEN_PATH).header(TestAuthenticationFilter.AUTHENTICATED_HEADER, "yes"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CampusErrorCodes.PERMISSION_DENIED.getCode()))
                .andExpect(jsonPath("$.message").value("没有该操作权限"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists(CampusApiContract.HEADER_TRACE_ID));
    }

    @Test
    void unauthenticatedNonCampusPath_shouldKeepUpstreamEnvelopeAndStatus() throws Exception {
        String body = mockMvc.perform(get(UPSTREAM_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("账号未登录"))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(header().doesNotExist(CampusApiContract.HEADER_TRACE_ID))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("traceId");
    }

    @Test
    void beanPostProcessor_shouldWrapOnlyFixedUpstreamSecurityBeans() {
        CampusSecurityHandlerBeanPostProcessor processor = new CampusSecurityHandlerBeanPostProcessor();
        Object authentication = processor.postProcessAfterInitialization(
                new AuthenticationEntryPointImpl(), "authenticationEntryPoint");
        Object denied = processor.postProcessAfterInitialization(new AccessDeniedHandlerImpl(), "accessDeniedHandler");
        Object unrelated = new Object();

        assertThat(authentication).isInstanceOf(CampusDelegatingAuthenticationEntryPoint.class);
        assertThat(denied).isInstanceOf(CampusDelegatingAccessDeniedHandler.class);
        assertThat(processor.postProcessAfterInitialization(unrelated, "other")).isSameAs(unrelated);
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class TestConfiguration {

        @Bean
        TestController testController() {
            return new TestController();
        }

        @Bean
        AuthenticationEntryPoint authenticationEntryPoint() {
            return new CampusDelegatingAuthenticationEntryPoint(new AuthenticationEntryPointImpl());
        }

        @Bean
        AccessDeniedHandler accessDeniedHandler() {
            return new CampusDelegatingAccessDeniedHandler(new AccessDeniedHandlerImpl());
        }

        @Bean
        TestAuthenticationFilter testAuthenticationFilter() {
            return new TestAuthenticationFilter();
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http,
                                                    AuthenticationEntryPoint authenticationEntryPoint,
                                                    AccessDeniedHandler accessDeniedHandler,
                                                    TestAuthenticationFilter testAuthenticationFilter)
                throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(authenticationEntryPoint)
                            .accessDeniedHandler(accessDeniedHandler))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(CAMPUS_FORBIDDEN_PATH).hasAuthority("campus:allowed")
                            .anyRequest().authenticated())
                    .addFilterBefore(testAuthenticationFilter, AuthorizationFilter.class);
            return http.build();
        }

    }

    @RestController
    static class TestController {

        @GetMapping({CAMPUS_PATH, CAMPUS_FORBIDDEN_PATH, UPSTREAM_PATH})
        String secured() {
            return "ok";
        }

    }

    static class TestAuthenticationFilter extends OncePerRequestFilter {

        static final String AUTHENTICATED_HEADER = "X-Test-Authenticated";

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (request.getHeader(AUTHENTICATED_HEADER) != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken("test-user", "n/a", List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            // SecurityContextHolderFilter 负责请求结束清理；这里不能提前 clear，
            // 否则 ExceptionTranslationFilter 无法区分匿名 401 与已认证 403。
            chain.doFilter(request, response);
        }

    }

}
