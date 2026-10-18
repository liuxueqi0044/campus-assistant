package cn.iocoder.yudao.module.campus.common.api.json;

/**
 * 时间字段格式非法（缺少时区偏移、传了时间戳数字等）。
 */
public class CampusTimestampFormatException extends RuntimeException {

    private final String field;

    public CampusTimestampFormatException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }

}
