package cn.iocoder.yudao.module.campus.common.api.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/** 只接受 JSON 字符串，禁止 Jackson 把数字 ID 静默强制转换为字符串或 Long。 */
public class CampusIdStringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw new CampusIdFormatException(fieldName(parser), "标识字段必须使用 JSON 字符串");
        }
        return parser.getText();
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
