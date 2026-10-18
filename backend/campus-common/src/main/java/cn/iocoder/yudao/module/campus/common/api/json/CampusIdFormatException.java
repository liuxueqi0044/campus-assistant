package cn.iocoder.yudao.module.campus.common.api.json;

/** 标识字段不是 JSON 字符串。 */
public class CampusIdFormatException extends RuntimeException {

    private final String field;

    public CampusIdFormatException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }

}
