package cn.iocoder.yudao.module.campus.common.transaction;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("M00.03")
class CampusTransactionConfigurationTest {

    @Test
    void shouldExposeTransactionExecutorAsSpringBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("transactionManager", new NoopTransactionManager());
            context.register(CampusTransactionConfiguration.class);
            context.refresh();

            assertThat(context.getBean("campusTransactionExecutor", TransactionExecutor.class)).isNotNull();
        }
    }

    private static final class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
