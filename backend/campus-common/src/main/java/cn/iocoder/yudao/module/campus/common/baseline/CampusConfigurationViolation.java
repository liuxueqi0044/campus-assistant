package cn.iocoder.yudao.module.campus.common.baseline;

/**
 * 一条启动配置校验不通过项。
 *
 * <p>只承载“配置项名称 + 原因标识”，不承载配置取值，保证异常与日志不会泄漏口令、密钥等秘密。
 *
 * @param variable 配置项名称（环境变量名或 Spring 配置属性名）
 * @param reason   原因标识，稳定取值见 {@link CampusStartupConfigurationValidator}
 */
public record CampusConfigurationViolation(String variable, String reason) {

    /**
     * 用于异常消息与日志的输出形式，形如 {@code CAMPUS_DB_PASSWORD(MISSING)}。
     */
    public String describe() {
        return variable + "(" + reason + ")";
    }

}
