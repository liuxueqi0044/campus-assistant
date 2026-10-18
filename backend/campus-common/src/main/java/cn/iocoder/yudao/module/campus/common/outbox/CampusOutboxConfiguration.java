package cn.iocoder.yudao.module.campus.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/** Spring wiring for the M00.05 database outbox. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CampusOutboxProperties.class)
public class CampusOutboxConfiguration {

    @Bean
    public OutboxRepository campusOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcOutboxRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    public OutboxPublisher campusOutboxPublisher(OutboxRepository repository,
                                                 CampusOutboxProperties properties) {
        return new OutboxPublisher(repository, properties.getMaxAttempts());
    }

    @Bean
    public EventHandlerRegistry campusEventHandlerRegistry(ObjectProvider<OutboxConsumer> consumers) {
        List<OutboxConsumer> orderedConsumers = consumers.orderedStream().toList();
        return new EventHandlerRegistry(orderedConsumers);
    }

    @Bean
    public OutboxDispatcher campusOutboxDispatcher(PlatformTransactionManager transactionManager,
                                                   OutboxRepository repository,
                                                   EventHandlerRegistry registry,
                                                   CampusOutboxProperties properties) {
        return new OutboxDispatcher(transactionManager, repository, registry,
                properties.getWorkerId(), properties.getBatchSize(), properties.getLeaseDuration(),
                properties.getInitialBackoff(), properties.getMaxBackoff());
    }

    @Bean
    public OutboxReplayService campusOutboxReplayService(OutboxRepository repository) {
        return new OutboxReplayService(repository);
    }

    @Bean
    public OutboxClaimJob campusOutboxClaimWorker(OutboxDispatcher dispatcher) {
        return new OutboxClaimJob(dispatcher);
    }
}
