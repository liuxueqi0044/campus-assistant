package cn.iocoder.yudao.module.campus.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Spring wiring for the M00.04 durable implementation. */
@Configuration(proxyBeanMethods = false)
public class CampusIdempotencyConfiguration {

    @Bean
    public RequestCanonicalizer campusRequestCanonicalizer(ObjectMapper objectMapper) {
        return new RequestCanonicalizer(objectMapper);
    }

    @Bean
    public IdempotencyRepository campusIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcIdempotencyRepository(jdbcTemplate);
    }

    @Bean
    public IdempotentCommandExecutor campusIdempotentCommandExecutor(
            PlatformTransactionManager transactionManager,
            IdempotencyRepository repository,
            RequestCanonicalizer canonicalizer,
            ObjectMapper objectMapper) {
        return new IdempotentCommandExecutor(transactionManager, repository, canonicalizer, objectMapper);
    }
}
