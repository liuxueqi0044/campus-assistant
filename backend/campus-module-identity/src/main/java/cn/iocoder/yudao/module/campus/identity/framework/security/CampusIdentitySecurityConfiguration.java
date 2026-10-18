package cn.iocoder.yudao.module.campus.identity.framework.security;

import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers M01.02's precise public-route customizer with Yudao's shared chain. */
@Configuration(proxyBeanMethods = false)
public class CampusIdentitySecurityConfiguration {

    @Bean("campusIdentityAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer campusIdentityAuthorizeRequestsCustomizer() {
        return new CampusIdentityAuthorizeRequestsCustomizer();
    }

}
