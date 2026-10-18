package cn.iocoder.yudao.module.campus.common.baseline;

import org.springframework.core.env.PropertyResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * M00.01 启动配置校验规则（纯逻辑，可独立验收）。
 *
 * <p>规则分两类：
 * <ol>
 *     <li>非生产环境：数据库主库与缓存地址必须有可解析取值，避免“配置缺项但应用静默启动”。</li>
 *     <li>生产环境：必需外部变量必须显式提供，禁止模拟支付网关、模拟登录，禁止沿用开发默认口令。</li>
 * </ol>
 *
 * <p>校验只读取配置项是否存在与是否为已知禁用取值，任何情况下都不把配置取值写入异常或日志。
 */
public class CampusStartupConfigurationValidator {

    /** 视为生产环境的 profile 名称 */
    public static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production");

    /** 任何 profile 都必须具备的数据库与缓存配置项 */
    public static final List<String> REQUIRED_PROPERTIES = List.of(
            "spring.datasource.dynamic.datasource.master.url",
            "spring.datasource.dynamic.datasource.master.username",
            "spring.datasource.dynamic.datasource.master.password",
            "spring.data.redis.host");

    /**
     * 生产环境必须由部署方显式提供的外部变量。
     *
     * <p>CAMPUS_REDIS_PORT 有明确默认值，故不在此列。微信公众号与小程序配置虽然不会由校园
     * 领域代码在启动阶段主动调用，但上游 WxJava starter 会创建客户端，因此必须在依赖初始化前
     * 给出可理解的 CONFIGURATION_INVALID，而不是晚到 Bean 创建阶段才失败。
     */
    public static final List<String> REQUIRED_PRODUCTION_ENVIRONMENT_VARIABLES = List.of(
            "CAMPUS_DB_URL",
            "CAMPUS_DB_USERNAME",
            "CAMPUS_DB_PASSWORD",
            "CAMPUS_REDIS_HOST",
            "CAMPUS_REDIS_PASSWORD",
            "CAMPUS_MYBATIS_ENCRYPTOR_PASSWORD",
            "CAMPUS_WECHAT_MP_APP_ID",
            "CAMPUS_WECHAT_MP_SECRET",
            "CAMPUS_WECHAT_MINIAPP_APP_ID",
            "CAMPUS_WECHAT_MINIAPP_SECRET",
            "CAMPUS_PAYMENT_ORDER_NOTIFY_URL",
            "CAMPUS_PAYMENT_REFUND_NOTIFY_URL",
            "CAMPUS_PAYMENT_TRANSFER_NOTIFY_URL");

    /** 上游开发配置中的默认口令，生产环境禁止沿用 */
    public static final Set<String> FORBIDDEN_PRODUCTION_PASSWORDS = Set.of("123456", "root", "Yudao@2024");

    /** 支付网关配置项，见 M08 与 M00.01 生产环境约束 */
    public static final String PAYMENT_GATEWAY_PROPERTY = "campus.payment.gateway";

    /** 模拟支付网关取值 */
    public static final String MOCK_PAYMENT_GATEWAY = "mock";

    /** 上游模拟登录开关，生产环境必须关闭 */
    public static final String SECURITY_MOCK_ENABLE_PROPERTY = "yudao.security.mock-enable";

    /** 主库口令属性名，仅用于判断是否沿用开发默认口令 */
    public static final String MASTER_PASSWORD_PROPERTY = "spring.datasource.dynamic.datasource.master.password";

    public static final String REASON_MISSING = "MISSING";
    public static final String REASON_DEFAULT_VALUE_FORBIDDEN = "DEFAULT_VALUE_FORBIDDEN";
    public static final String REASON_MOCK_FORBIDDEN = "MOCK_FORBIDDEN";

    private final Function<String, String> environmentVariables;

    /**
     * @param environmentVariables 外部变量读取函数，生产传入 {@code System::getenv}，测试可注入固定视图
     */
    public CampusStartupConfigurationValidator(Function<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public boolean isProductionProfile(String[] activeProfiles) {
        if (activeProfiles == null) {
            return false;
        }
        return Arrays.stream(activeProfiles)
                .filter(profile -> profile != null && !profile.isBlank())
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(PRODUCTION_PROFILES::contains);
    }

    /**
     * 校验当前环境，返回全部不通过项；返回空列表表示允许启动。
     */
    public List<CampusConfigurationViolation> validate(String[] activeProfiles, PropertyResolver properties) {
        List<CampusConfigurationViolation> violations = new ArrayList<>();
        if (isProductionProfile(activeProfiles)) {
            appendProductionEnvironmentVariableViolations(violations);
            appendProductionPaymentGatewayViolations(violations, properties);
            appendProductionSecurityViolations(violations, properties);
            appendProductionPasswordViolations(violations, properties);
        } else {
            appendRequiredPropertyViolations(violations, properties);
        }
        return List.copyOf(violations);
    }

    private void appendRequiredPropertyViolations(List<CampusConfigurationViolation> violations,
                                                  PropertyResolver properties) {
        for (String property : REQUIRED_PROPERTIES) {
            if (isBlank(propertyValue(properties, property))) {
                violations.add(new CampusConfigurationViolation(property, REASON_MISSING));
            }
        }
    }

    private void appendProductionEnvironmentVariableViolations(List<CampusConfigurationViolation> violations) {
        for (String variable : REQUIRED_PRODUCTION_ENVIRONMENT_VARIABLES) {
            if (isBlank(environmentVariables.apply(variable))) {
                violations.add(new CampusConfigurationViolation(variable, REASON_MISSING));
            }
        }
    }

    private void appendProductionPaymentGatewayViolations(List<CampusConfigurationViolation> violations,
                                                          PropertyResolver properties) {
        String gateway = propertyValue(properties, PAYMENT_GATEWAY_PROPERTY);
        if (isBlank(gateway)) {
            violations.add(new CampusConfigurationViolation(PAYMENT_GATEWAY_PROPERTY, REASON_MISSING));
            return;
        }
        if (MOCK_PAYMENT_GATEWAY.equalsIgnoreCase(gateway.trim())) {
            violations.add(new CampusConfigurationViolation(PAYMENT_GATEWAY_PROPERTY, REASON_MOCK_FORBIDDEN));
        }
    }

    private void appendProductionSecurityViolations(List<CampusConfigurationViolation> violations,
                                                    PropertyResolver properties) {
        String mockEnable = propertyValue(properties, SECURITY_MOCK_ENABLE_PROPERTY);
        if (mockEnable != null && Boolean.parseBoolean(mockEnable.trim())) {
            violations.add(new CampusConfigurationViolation(SECURITY_MOCK_ENABLE_PROPERTY, REASON_MOCK_FORBIDDEN));
        }
    }

    private void appendProductionPasswordViolations(List<CampusConfigurationViolation> violations,
                                                    PropertyResolver properties) {
        String password = propertyValue(properties, MASTER_PASSWORD_PROPERTY);
        if (!isBlank(password) && FORBIDDEN_PRODUCTION_PASSWORDS.contains(password.trim())) {
            violations.add(new CampusConfigurationViolation(MASTER_PASSWORD_PROPERTY, REASON_DEFAULT_VALUE_FORBIDDEN));
        }
    }

    /**
     * 读取配置项。占位符无法解析（例如 {@code ${CAMPUS_DB_URL}} 未注入）时按“缺少”处理，
     * 而不是让启动抛出难以理解的占位符异常。
     */
    private static String propertyValue(PropertyResolver properties, String property) {
        try {
            return properties.getProperty(property);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}
