package cn.iocoder.yudao.module.campus.common.transaction;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Standard application wiring for the M00.03 REQUIRED transaction executor. */
@Configuration(proxyBeanMethods = false)
public class CampusTransactionConfiguration {

    @Bean
    public TransactionExecutor campusTransactionExecutor(PlatformTransactionManager transactionManager) {
        return new TransactionExecutor(transactionManager);
    }
}
