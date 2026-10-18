package cn.iocoder.yudao.module.campus.identity.framework.security;

import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Explicit public-route wiring for M01.02.
 *
 * <p>The upstream annotation scan is based on the final MVC mapping and can be
 * affected by package path-prefixing during bootstrap. These are the only M01.02
 * routes that may bypass authentication; methods and paths intentionally stay
 * exact so this does not create a blanket campus API exception.</p>
 */
public final class CampusIdentityAuthorizeRequestsCustomizer extends AuthorizeRequestsCustomizer {

    @Override
    public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        registry.requestMatchers(HttpMethod.GET, buildAppApi("/campus/v1/schools")).permitAll()
                .requestMatchers(HttpMethod.GET, buildAppApi("/campus/v1/context")).permitAll()
                .requestMatchers(HttpMethod.PUT, buildAppApi("/campus/v1/context")).permitAll()
                // M01.01 is intentionally a single, method-specific anonymous route.
                .requestMatchers(HttpMethod.POST, buildAppApi("/campus/v1/auth/wechat/login")).permitAll()
                .requestMatchers(HttpMethod.POST, buildAppApi("/campus/v1/auth/logout")).permitAll();
    }

}
