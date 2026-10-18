package cn.iocoder.yudao.module.campus.common.api.json;

/**
 * 金额字段格式非法（例如 JSON 中传了小数或字符串）。
 *
 * <p>由 {@link MoneyFenDeserializer} 抛出，Jackson 会把它包进 {@code JsonMappingException}，
 * 最终由 {@code CampusExceptionHandler} 映射成冻结的 400 错误码。
 */
public class CampusMoneyFormatException extends RuntimeException {

    private final String field;

    public CampusMoneyFormatException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }

}
