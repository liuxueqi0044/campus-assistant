package cn.iocoder.yudao.module.campus.identity.framework.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the campus tenant pin between tenant context parsing and tenant security. */
@Configuration(proxyBeanMethods = false)
public class CampusFixedTenantConfiguration {

    @Bean
    public FilterRegistrationBean<CampusFixedTenantWebFilter> campusFixedTenantWebFilter(
            @Value("${campus.platform-tenant-id:1}") long platformTenantId) {
        FilterRegistrationBean<CampusFixedTenantWebFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CampusFixedTenantWebFilter(platformTenantId));
        registration.setOrder(CampusFixedTenantWebFilter.ORDER);
        return registration;
    }
}
