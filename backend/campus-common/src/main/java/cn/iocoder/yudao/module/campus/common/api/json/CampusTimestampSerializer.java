package cn.iocoder.yudao.module.campus.common.api.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 把 {@link OffsetDateTime} 固定输出为带偏移的 RFC 3339 字符串。
 *
 * <p>必要性：上游 {@code application.yaml} 打开了
 * {@code spring.jackson.serialization.write-dates-as-timestamps}，时间字段默认会输出
 * epoch 数字。契约要求「时间使用明确格式」，因此校园 DTO 的时间字段必须显式标注
 * {@link CampusTimestamp}，不能依赖全局 Jackson 开关。
 */
public class CampusTimestampSerializer extends JsonSerializer<OffsetDateTime> {

    /** 契约时间格式，例如 {@code 2026-09-17T10:15:30+08:00} */
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(FORMATTER.format(value));
    }

}
