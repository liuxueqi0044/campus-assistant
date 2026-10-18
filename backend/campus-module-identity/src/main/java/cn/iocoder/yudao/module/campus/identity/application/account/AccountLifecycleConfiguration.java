package cn.iocoder.yudao.module.campus.identity.application.account;

import cn.iocoder.yudao.module.campus.identity.application.port.AccountObligationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AccountLifecycleConfiguration {

    @Bean
    @ConditionalOnMissingBean(AccountObligationPort.class)
    public AccountObligationPort unavailableAccountObligationPort() {
        return userId -> {
            throw new AccountLifecycleException(AccountLifecycleFailure.OBLIGATION_UNAVAILABLE);
        };
    }
}
