package cn.iocoder.yudao.module.campus.common.api.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 把 {@link MoneyFen} 输出为整数分，不带引号也不带小数位。
 */
public class MoneyFenSerializer extends JsonSerializer<MoneyFen> {

    @Override
    public void serialize(MoneyFen value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeNumber(value.getCents());
    }

}
