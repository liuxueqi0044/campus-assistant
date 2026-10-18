package cn.iocoder.yudao.module.campus.common.health;

import cn.iocoder.yudao.module.campus.common.api.CampusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M00.01 验收（A 级）：{@code /health/readiness} 只给必要状态，必需依赖缺失时不可接流量。
 */
class CampusReadinessControllerTest {

    @Test
    void readiness_allRequiredDependenciesReachable_shouldOnlyExposeUpStatus() {
        CampusReadinessController controller = controller(true, true);

        CampusResponse<Map<String, Object>> result = controller.readiness();

        assertThat(result.getData()).containsOnlyKeys("status");
        assertThat(result.getData().get("status")).isEqualTo(CampusReadinessController.STATUS_UP);
    }

    @Test
    void readiness_responseShouldCarryTraceIdAndCampusEnvelope() {
        CampusReadinessController controller = controller(true, true);

        CampusResponse<Map<String, Object>> result = controller.readiness();

        // M00.02：成功响应同样带 traceId，成功提示为空字符串
        assertThat(result.getCode()).isZero();
        assertThat(result.getMessage()).isEmpty();
        assertThat(result.getTraceId()).isNotBlank();
    }

    @Test
    void readiness_unreachableDatabase_shouldReportDownWithoutDetails() {
        CampusReadinessController controller = controller(false, true);

        CampusResponse<Map<String, Object>> result = controller.readiness();

        assertThat(result.getData()).containsOnlyKeys("status");
        assertThat(result.getData().get("status")).isEqualTo(CampusReadinessController.STATUS_DOWN);
    }

    @Test
    void readiness_missingCache_shouldReportDownInsteadOfTreatingUnknownAsUp() {
        CampusReadinessController controller = controller(true, false);

        CampusResponse<Map<String, Object>> result = controller.readiness();

        assertThat(result.getData()).containsOnlyKeys("status");
        assertThat(result.getData().get("status")).isEqualTo(CampusReadinessController.STATUS_DOWN);
    }

    private static CampusReadinessController controller(boolean databaseReachable, boolean cacheAvailable) {
        return new CampusReadinessController(
                new FixedObjectProvider<>(dataSource(databaseReachable)),
                new FixedObjectProvider<>(cacheAvailable ? redisConnectionFactory() : null));
    }

    private static DataSource dataSource(boolean reachable) {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        try {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isValid(anyInt())).thenReturn(reachable);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return dataSource;
    }

    private static RedisConnectionFactory redisConnectionFactory() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        return connectionFactory;
    }

    /**
     * 固定实例的 {@link ObjectProvider}，避免为了单元测试启动 Spring 容器。
     */
    private static final class FixedObjectProvider<T> implements ObjectProvider<T> {

        private final T value;

        private FixedObjectProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject() throws BeansException {
            return value;
        }

        @Override
        public T getObject(Object... args) throws BeansException {
            return value;
        }

        @Override
        public T getIfAvailable() throws BeansException {
            return value;
        }

        @Override
        public T getIfUnique() throws BeansException {
            return value;
        }

        @Override
        public Iterator<T> iterator() {
            return value == null ? Collections.emptyIterator() : List.of(value).iterator();
        }

    }

}
