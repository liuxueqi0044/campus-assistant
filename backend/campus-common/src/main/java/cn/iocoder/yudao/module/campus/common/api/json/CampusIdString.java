package cn.iocoder.yudao.module.campus.common.api.json;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * M00.02 标识字段标记：被标记的字段在 JSON 中固定输出字符串。
 *
 * <p>上游只把超出 JavaScript 安全整数的 {@code Long} 转成字符串（见 {@code NumberSerializer}），
 * 这会让「同样是 ID，有时是数字有时是字符串」在客户端产生分支。校园契约要求 ID 恒为字符串，
 * 因此 DTO 中的标识字段必须显式标注本注解。
 *
 * <p>公开 DTO 的字段类型必须是 {@link String}。反序列化器拒绝 JSON 数字，避免调用方在
 * JavaScript 中已发生精度损失后，后端又把错误数字静默接受为合法 ID。
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = ToStringSerializer.class)
@JsonDeserialize(using = CampusIdStringDeserializer.class)
public @interface CampusIdString {
}
