package cn.iocoder.yudao.module.campus.common.baseline;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M00.01 验收（A 级）：启动守卫必须在依赖初始化之前中止启动。
 */
class CampusStartupConfigurationGuardTest {

    private static final Map<String, String> COMPLETE_PRODUCTION_ENVIRONMENT = Map.ofEntries(
            Map.entry("CAMPUS_DB_URL", "jdbc:mysql://db.internal:3306/campus"),
            Map.entry("CAMPUS_DB_USERNAME", "campus_app"),
            Map.entry("CAMPUS_DB_PASSWORD", "prod-password-not-in-message"),
            Map.entry("CAMPUS_REDIS_HOST", "redis.internal"),
            Map.entry("CAMPUS_REDIS_PASSWORD", "production-cache-secret-not-in-message"),
            Map.entry("CAMPUS_MYBATIS_ENCRYPTOR_PASSWORD", "production-encryptor-secret-not-in-message"),
            Map.entry("CAMPUS_WECHAT_MP_APP_ID", "wx-production-mp-app-id"),
            Map.entry("CAMPUS_WECHAT_MP_SECRET", "production-mp-secret-not-in-message"),
            Map.entry("CAMPUS_WECHAT_MINIAPP_APP_ID", "wx-production-miniapp-app-id"),
            Map.entry("CAMPUS_WECHAT_MINIAPP_SECRET", "production-miniapp-secret-not-in-message"),
            Map.entry("CAMPUS_PAYMENT_ORDER_NOTIFY_URL", "https://api.example.test/pay/order"),
            Map.entry("CAMPUS_PAYMENT_REFUND_NOTIFY_URL", "https://api.example.test/pay/refund"),
            Map.entry("CAMPUS_PAYMENT_TRANSFER_NOTIFY_URL", "https://api.example.test/pay/transfer"));

    private static MockEnvironment productionEnvironment(String paymentGateway) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty(CampusStartupConfigurationValidator.PAYMENT_GATEWAY_PROPERTY, paymentGateway);
        environment.setProperty(CampusStartupConfigurationValidator.SECURITY_MOCK_ENABLE_PROPERTY, "false");
        return environment;
    }

    private static MockEnvironment localEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        environment.setProperty("spring.datasource.dynamic.datasource.master.url",
                "jdbc:mysql://127.0.0.1:3306/campus");
        environment.setProperty("spring.datasource.dynamic.datasource.master.username", "root");
        environment.setProperty("spring.datasource.dynamic.datasource.master.password", "123456");
        environment.setProperty("spring.data.redis.host", "127.0.0.1");
        return environment;
    }

    @Test
    void postProcessEnvironment_productionWithoutEnvironmentVariables_shouldAbortWithConfigurationInvalid() {
        CampusStartupConfigurationGuard guard = new CampusStartupConfigurationGuard(name -> null);
        MockEnvironment environment = productionEnvironment("wechat");

        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(CampusConfigurationException.class)
                .hasMessageContaining(CampusConfigurationException.ERROR_CODE)
                .hasMessageContaining("CAMPUS_DB_URL(MISSING)")
                .hasMessageContaining("CAMPUS_REDIS_HOST(MISSING)")
                .hasMessageContaining("CAMPUS_REDIS_PASSWORD(MISSING)")
                .hasMessageContaining("CAMPUS_MYBATIS_ENCRYPTOR_PASSWORD(MISSING)")
                .hasMessageContaining("CAMPUS_WECHAT_MP_APP_ID(MISSING)")
                .hasMessageContaining("CAMPUS_WECHAT_MINIAPP_APP_ID(MISSING)")
                .hasMessageContaining("CAMPUS_PAYMENT_ORDER_NOTIFY_URL(MISSING)");
    }

    @Test
    void postProcessEnvironment_productionWithMockGateway_shouldAbortWithConfigurationInvalid() {
        CampusStartupConfigurationGuard guard =
                new CampusStartupConfigurationGuard(COMPLETE_PRODUCTION_ENVIRONMENT::get);
        MockEnvironment environment = productionEnvironment("mock");

        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(CampusConfigurationException.class)
                .hasMessageContaining("campus.payment.gateway(MOCK_FORBIDDEN)");
    }

    @Test
    void postProcessEnvironment_productionWithCompleteConfiguration_shouldAllowStartup() {
        CampusStartupConfigurationGuard guard =
                new CampusStartupConfigurationGuard(COMPLETE_PRODUCTION_ENVIRONMENT::get);
        MockEnvironment environment = productionEnvironment("wechat");

        assertThatCode(() -> guard.postProcessEnvironment(environment, new SpringApplication()))
                .doesNotThrowAnyException();
    }

    @Test
    void postProcessEnvironment_localWithUpstreamDefaults_shouldAllowStartup() {
        CampusStartupConfigurationGuard guard = new CampusStartupConfigurationGuard(name -> null);
        MockEnvironment environment = localEnvironment();

        assertThatCode(() -> guard.postProcessEnvironment(environment, new SpringApplication()))
                .doesNotThrowAnyException();
    }

    @Test
    void getOrder_shouldRunAfterConfigurationDataIsLoaded() {
        CampusStartupConfigurationGuard guard = new CampusStartupConfigurationGuard(name -> null);

        // 必须晚于配置数据加载（HIGHEST_PRECEDENCE + 10），早于上游读取数据源地址的 EnvironmentPostProcessor
        assertThat(guard.getOrder()).isGreaterThan(Ordered.HIGHEST_PRECEDENCE + 10)
                .isLessThan(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void exceptionMessage_shouldNotContainConfigurationValues() {
        CampusStartupConfigurationGuard guard =
                new CampusStartupConfigurationGuard(COMPLETE_PRODUCTION_ENVIRONMENT::get);
        MockEnvironment environment = productionEnvironment("mock");

        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(CampusConfigurationException.class)
                .hasMessageNotContaining("prod-password-not-in-message");
    }

}
