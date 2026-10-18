package cn.iocoder.yudao.module.campus.common.api.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * 只接受带时区偏移的 RFC 3339 字符串。
 *
 * <p>epoch 数字与缺少偏移的本地时间都会被拒绝：前者无法区分时区，后者会让跨时区学校算错日期。
 */
public class CampusTimestampDeserializer extends JsonDeserializer<OffsetDateTime> {

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw new CampusTimestampFormatException(fieldName(parser),
                    "时间字段必须使用带时区偏移的 RFC 3339 字符串");
        }
        try {
            return OffsetDateTime.parse(parser.getText(), CampusTimestampSerializer.FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new CampusTimestampFormatException(fieldName(parser),
                    "时间字段必须使用带时区偏移的 RFC 3339 字符串");
        }
    }

    private static String fieldName(JsonParser parser) {
        try {
            String name = parser.currentName();
            return name != null ? name : "<unknown>";
        } catch (IOException ex) {
            return "<unknown>";
        }
    }

}
