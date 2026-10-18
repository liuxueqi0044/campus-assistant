package cn.iocoder.yudao.module.campus.common.api.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * 只接受 JSON 整数（{@code VALUE_NUMBER_INT}）。
 *
 * <p>小数、字符串、布尔、null 一律拒绝：宁可让调用方看到 400，也不能让金额被静默截断。
 */
public class MoneyFenDeserializer extends JsonDeserializer<MoneyFen> {

    @Override
    public MoneyFen deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return MoneyFen.of(parser.getLongValue());
        }
        throw new CampusMoneyFormatException(fieldName(parser),
                "金额字段必须使用整数分，实际收到 " + parser.currentToken());
    }

    private static String fieldName(JsonParser parser) {
        try {
            String name = parser.currentName();
            return name != null ? name : "<unknown>";
        } catch (IOException ex) {
            // 取不到字段名不影响错误语义，退化为占位符
            return "<unknown>";
        }
    }

}
