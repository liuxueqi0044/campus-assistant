package cn.iocoder.yudao.module.campus.common.baseline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * M00.01 启动链第一步之后的守卫：配置加载 → <b>验证必需变量</b> → 初始化依赖 → 健康检查。
 *
 * <p>以 {@link EnvironmentPostProcessor} 形式实现，保证在配置数据（application-*.yaml）加载完成之后、
 * 任何依赖（数据源、缓存、支付适配器）初始化之前执行；校验不通过直接中止启动，标识为
 * {@link CampusConfigurationException#ERROR_CODE}。
 *
 * <p>注册位置见 {@code META-INF/spring.factories}。
 */
public class CampusStartupConfigurationGuard implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CampusStartupConfigurationGuard.class);

    private final Function<String, String> environmentVariables;

    public CampusStartupConfigurationGuard() {
        this(System::getenv);
    }

    /**
     * 仅供测试注入环境变量视图；正常启动使用 {@link #CampusStartupConfigurationGuard()}。
     */
    public CampusStartupConfigurationGuard(Function<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<CampusConfigurationViolation> violations =
                new CampusStartupConfigurationValidator(environmentVariables)
                        .validate(environment.getActiveProfiles(), environment);
        if (violations.isEmpty()) {
            return;
        }
        // 只输出配置项名称与原因标识：该阶段日志系统尚未完全初始化，失败细节同时写入异常消息。
        log.error("[postProcessEnvironment][校园助手启动配置校验未通过，涉及配置项({})]",
                violations.stream().map(CampusConfigurationViolation::describe).collect(Collectors.joining(", ")));
        throw new CampusConfigurationException(violations);
    }

    @Override
    public int getOrder() {
        // 顺序要求：晚于 Spring Boot 的 ConfigDataEnvironmentPostProcessor（HIGHEST_PRECEDENCE + 10，加载 application-*.yaml），
        // 早于上游 yudao 的 EnvironmentPostProcessor（如 IdTypeEnvironmentPostProcessor 会直接读取数据源地址；
        // 缺少环境变量时它会先抛出难以理解的占位符异常，掩盖真正的配置缺失原因）。
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

}
