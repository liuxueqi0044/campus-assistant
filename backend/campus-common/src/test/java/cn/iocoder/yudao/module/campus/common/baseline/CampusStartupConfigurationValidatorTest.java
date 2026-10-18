package cn.iocoder.yudao.module.campus.common.baseline;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M00.01 验收（A 级）：启动配置校验规则。
 *
 * <p>覆盖“删除必需数据库变量，启动拒绝”“生产启用模拟网关，启动拒绝”以及不泄漏配置取值的约束。
 */
class CampusStartupConfigurationValidatorTest {

    /** 模拟“部署环境完全没有注入 CAMPUS_* 变量” */
    private static final Function<String, String> NO_ENVIRONMENT_VARIABLES = name -> null;

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

    private static MockEnvironment productionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty(CampusStartupConfigurationValidator.PAYMENT_GATEWAY_PROPERTY, "wechat");
        environment.setProperty(CampusStartupConfigurationValidator.SECURITY_MOCK_ENABLE_PROPERTY, "false");
        return environment;
    }

    private static List<String> variables(List<CampusConfigurationViolation> violations) {
        return violations.stream().map(CampusConfigurationViolation::variable).toList();
    }

    @Test
    void validate_localProfileWithCompleteConfiguration_shouldPass() {
        MockEnvironment environment = localEnvironment();

        List<CampusConfigurationViolation> violations = new CampusStartupConfigurationValidator(NO_ENVIRONMENT_VARIABLES)
                .validate(environment.getActiveProfiles(), environment);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_localProfileMissingRequiredDatabaseProperty_shouldReportMissingVariable() {
        MockEnvironment environment = localEnvironment();
        environment.setProperty("spring.datasource.dynamic.datasource.master.password", "");

        List<CampusConfigurationViolation> violations = new CampusStartupConfigurationValidator(NO_ENVIRONMENT_VARIABLES)
                .validate(environment.getActiveProfiles(), environment);

        assertThat(variables(violations))
                .containsExactly("spring.datasource.dynamic.datasource.master.password");
        assertThat(violations.get(0).reason()).isEqualTo(CampusStartupConfigurationValidator.REASON_MISSING);
    }

    @Test
    void validate_localProfileUnresolvablePlaceholder_shouldBeTreatedAsMissing() {
        MockEnvironment environment = localEnvironment();
        environment.setProperty("spring.data.redis.host", "${CAMPUS_REDIS_HOST}");

        List<CampusConfigurationViolation> violations = new CampusStartupConfigurationValidator(NO_ENVIRONMENT_VARIABLES)
                .validate(environment.getActiveProfiles(), environment);

        assertThat(variables(violations)).containsExactly("spring.data.redis.host");
    }

    @Test
    void validate_productionProfileWithoutEnvironmentVariables_shouldReportEveryRequiredVariableName() {
        MockEnvironment environment = productionEnvironment();

        List<CampusConfigurationViolation> violations = new CampusStartupConfigurationValidator(NO_ENVIRONMENT_VARIABLES)
                .validate(environment.getActiveProfiles(), environment);

        assertThat(variables(violations)).containsExactlyInAnyOrderElementsOf(
                CampusStartupConfigurationValidator.REQUIRED_PRODUCTION_ENVIRONMENT_VARIABLES);
        assertThat(violations).allSatisfy(violation ->
                assertThat(violation.reason()).isEqualTo(CampusStartupConfigurationValidator.REASON_MISSING));
    }

    @Test
    void validate_productionProfileWithMockPaymentGateway_shouldRejectStartup() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty(CampusStartupConfigurationValidator.PAYMENT_GATEWAY_PROPERTY, "mock");

        List<CampusConfigurationViolation> violations = new CampusStartupConfigurationValidator(
                COMPLETE_PRODUCTION_ENVIRONMENT::get)
                .validate(environment.getActiveProfiles(), environment);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.variable()).isEqualTo(CampusStartupConfigurationValidator.PAYMENT_GATEWAY_PROPERTY);
            assertThat(violation.reason()).isEqualTo(CampusStartupConfigurationValidator.REASON_MOCK_FORBIDDEN);
        });
    }

    @Test
    void validate_productionProfileWithoutPaymentGatewayProperty_shouldReportMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty(CampusStartupConfigurationValidator.SECURITY_MOCK_ENABLE_PROPERTY, "false");

        List<CampusConfigurationViolation> violations = new CampusStartupConfigurationValidator(
                COMPLETE_PRODUCTION_ENVIRONMENT::get)
                .validate(environment.getActiveProfiles(), environment);

        assertThat(variables(violations)).containsExactly(CampusStartupConfigurationValidator.PAYMENT_GATEWAY_PROPERTY);
    }

    @Test
    void validate_productionProfileWithMockLoginEnabled_shouldRejectStartup() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty(CampusStartupConfigurationValidator.SECURITY_MOCK_ENABLE_PROPERTY, "true");

        List<CampusConfigurationViolation> violations = new CampusStartupConfigurationValidator(
                COMPLETE_PRODUCTION_ENVIRONMENT::get)
                .validate(environment.getActiveProfiles(), environment);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.variable())
                    .isEqualTo(CampusStartupConfigurationValidator.SECURITY_MOCK_ENABLE_PROPERTY);
            assertThat(violation.reason()).isEqualTo(CampusStartupConfigurationValidator.REASON_MOCK_FORBIDDEN);
        });
    }

    @Test
    void validate_productionProfileWithDevelopmentDefaultPassword_shouldRejectStartup() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("spring.datasource.dynamic.datasource.master.password", "123456");

        List<CampusConfigurationViolation> violations = new CampusStartupConfigurationValidator(
                COMPLETE_PRODUCTION_ENVIRONMENT::get)
                .validate(environment.getActiveProfiles(), environment);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.reason())
                    .isEqualTo(CampusStartupConfigurationValidator.REASON_DEFAULT_VALUE_FORBIDDEN);
            // 违反项只暴露配置项名称，禁止携带默认口令取值
            assertThat(violation.describe()).doesNotContain("123456");
        });
    }

    @Test
    void validate_productionProfileWithCompleteEnvironment_shouldPass() {
        MockEnvironment environment = productionEnvironment();

        List<CampusConfigurationViolation> violations = new CampusStartupConfigurationValidator(
                COMPLETE_PRODUCTION_ENVIRONMENT::get)
                .validate(environment.getActiveProfiles(), environment);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_shouldTreatProductionAliasAndCaseInsensitively() {
        CampusStartupConfigurationValidator validator =
                new CampusStartupConfigurationValidator(NO_ENVIRONMENT_VARIABLES);

        assertThat(validator.isProductionProfile(new String[]{"PROD"})).isTrue();
        assertThat(validator.isProductionProfile(new String[]{"production"})).isTrue();
        assertThat(validator.isProductionProfile(new String[]{"local", "dev"})).isFalse();
        assertThat(validator.isProductionProfile(null)).isFalse();
    }

    @Test
    void configurationException_shouldExposeStableCodeAndOnlyVariableNames() {
        List<CampusConfigurationViolation> violations = List.of(
                new CampusConfigurationViolation("CAMPUS_DB_PASSWORD",
                        CampusStartupConfigurationValidator.REASON_MISSING));

        CampusConfigurationException exception = new CampusConfigurationException(violations);

        assertThat(exception.getErrorCode()).isEqualTo("CONFIGURATION_INVALID");
        assertThat(exception.getMessage())
                .contains("CONFIGURATION_INVALID")
                .contains("CAMPUS_DB_PASSWORD(MISSING)")
                .doesNotContain("prod-password-not-in-message");
    }

}
