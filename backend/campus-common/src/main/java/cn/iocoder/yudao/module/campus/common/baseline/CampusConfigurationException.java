package cn.iocoder.yudao.module.campus.common.baseline;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 启动配置校验失败（M00.01）。
 *
 * <p>错误标识固定为 {@link #ERROR_CODE}；消息只包含配置项名称与原因标识，禁止拼接配置取值，
 * 这样“删除必需数据库变量，启动拒绝且日志无秘密”可以按验收条目直接断言。
 */
public class CampusConfigurationException extends RuntimeException {

    /**
     * 稳定错误标识，M00.02 冻结数值编码时沿用该名称。
     */
    public static final String ERROR_CODE = "CONFIGURATION_INVALID";

    private final List<CampusConfigurationViolation> violations;

    public CampusConfigurationException(List<CampusConfigurationViolation> violations) {
        super(buildMessage(violations));
        this.violations = List.copyOf(violations);
    }

    private static String buildMessage(List<CampusConfigurationViolation> violations) {
        String variables = violations.stream()
                .map(CampusConfigurationViolation::describe)
                .collect(Collectors.joining(", "));
        return ERROR_CODE + ": 启动配置校验未通过，涉及配置项[" + variables
                + "]。请检查部署环境变量与 spring.profiles.active；该校验不会输出配置取值。";
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }

    public List<CampusConfigurationViolation> getViolations() {
        return violations;
    }

}
