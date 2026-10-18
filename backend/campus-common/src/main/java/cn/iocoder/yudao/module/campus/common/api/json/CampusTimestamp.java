package cn.iocoder.yudao.module.campus.common.api.json;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * M00.02 时间字段标记：JSON 中固定为带时区偏移的 RFC 3339 字符串。
 *
 * <p>校园 DTO 的技术时间字段必须标注本注解；课表等需要「学校本地日期 + 周次」的场景另按
 * M03 契约表达，不能用一个模糊的本地时间糊过去。
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = CampusTimestampSerializer.class)
@JsonDeserialize(using = CampusTimestampDeserializer.class)
public @interface CampusTimestamp {
}
