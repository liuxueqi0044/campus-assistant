package cn.iocoder.yudao.module.campus.common.api.security;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 在不修改芋道源码的前提下装饰其两个安全失败处理器。
 *
 * <p>Bean 名称来自固定上游 {@code YudaoSecurityAutoConfiguration}；非校园请求始终委托原 Bean，
 * 因而 system／infra 的 HTTP 200 + {@code code/msg/data} 兼容行为保持不变。
 */
@Component
public class CampusSecurityHandlerBeanPostProcessor implements BeanPostProcessor {

    static final String AUTHENTICATION_ENTRY_POINT_BEAN = "authenticationEntryPoint";
    static final String ACCESS_DENIED_HANDLER_BEAN = "accessDeniedHandler";

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (AUTHENTICATION_ENTRY_POINT_BEAN.equals(beanName)
                && bean instanceof AuthenticationEntryPoint entryPoint
                && !(bean instanceof CampusDelegatingAuthenticationEntryPoint)) {
            return new CampusDelegatingAuthenticationEntryPoint(entryPoint);
        }
        if (ACCESS_DENIED_HANDLER_BEAN.equals(beanName)
                && bean instanceof AccessDeniedHandler deniedHandler
                && !(bean instanceof CampusDelegatingAccessDeniedHandler)) {
            return new CampusDelegatingAccessDeniedHandler(deniedHandler);
        }
        return bean;
    }

}
