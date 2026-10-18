package cn.iocoder.yudao.module.campus.identity.framework.security;

import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.campus.identity.controller.app.CampusIdentitySecurityPublicController;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.filter.DelegatingFilterProxy;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Spring Security chain regression test for the M01.02 anonymous allow-list.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CampusIdentitySecurityFilterChainTest.TestConfiguration.class)
@WebAppConfiguration
class CampusIdentitySecurityFilterChainTest {

    private static final String ROOT = "/app-api/campus/v1";

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DelegatingFilterProxy security = new DelegatingFilterProxy("springSecurityFilterChain", applicationContext);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).addFilters(security).build();
    }

    @Test
    void anonymousAccessIsLimitedToTheFiveCanonicalMethodAndPathPairs() throws Exception {
        mockMvc.perform(get(ROOT + "/schools")).andExpect(status().isOk());
        mockMvc.perform(get(ROOT + "/context")).andExpect(status().isOk());
        mockMvc.perform(put(ROOT + "/context")).andExpect(status().isOk());
        mockMvc.perform(post(ROOT + "/auth/wechat/login")).andExpect(status().isOk());
        mockMvc.perform(post(ROOT + "/auth/logout")).andExpect(status().isOk());

        // Same path but an unlisted method must not inherit an allow-all rule.
        mockMvc.perform(post(ROOT + "/context")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ROOT + "/auth/wechat/login")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ROOT + "/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void campusFallbackAndUpstreamAdminPathRemainAuthenticated() throws Exception {
        mockMvc.perform(get(ROOT + "/not-existing")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin-api/system/secured")).andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(CampusIdentitySecurityConfiguration.class)
    static class TestConfiguration {

        @Bean
        WebProperties webProperties() {
            return new WebProperties();
        }

        @Bean
        CampusIdentitySecurityPublicController publicController() {
            return new CampusIdentitySecurityPublicController();
        }

        @Bean
        SecurityFilterChain campusIdentityTestSecurityFilterChain(HttpSecurity http,
                                                                    List<AuthorizeRequestsCustomizer> customizers) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                            new HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)))
                    .authorizeHttpRequests(registry -> {
                        customizers.forEach(customizer -> customizer.customize(registry));
                        registry.anyRequest().authenticated();
                    });
            return http.build();
        }
    }

}
