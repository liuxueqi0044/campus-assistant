package cn.iocoder.yudao.module.campus.common.api.json;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;

/**
 * M00.02 金额值类型：固定为「整数分」。
 *
 * <p>为什么需要专用类型：Jackson 默认允许把 {@code 12.5} 强制转换成 {@code 12}，金额会被静默
 * 改小。使用本类型后，小数、字符串与缺失值都会在反序列化阶段失败，由统一异常映射返回 400。
 *
 * <p>JSON 形态为不带引号的整数，例如 {@code "amountFen": 1234}（= 12.34 元）。
 */
@Getter
@EqualsAndHashCode
@JsonSerialize(using = MoneyFenSerializer.class)
@JsonDeserialize(using = MoneyFenDeserializer.class)
public final class MoneyFen implements Serializable, Comparable<MoneyFen> {

    /** 零分 */
    public static final MoneyFen ZERO = new MoneyFen(0L);

    /** 金额（分） */
    private final long cents;

    private MoneyFen(long cents) {
        this.cents = cents;
    }

    /** 以「分」为单位构造；允许负数以表达冲正与退款分录 */
    public static MoneyFen of(long cents) {
        return cents == 0L ? ZERO : new MoneyFen(cents);
    }

    public static MoneyFen ofYuan(long yuan) {
        return of(Math.multiplyExact(yuan, 100L));
    }

    public MoneyFen plus(MoneyFen other) {
        return of(Math.addExact(cents, other.cents));
    }

    public MoneyFen minus(MoneyFen other) {
        return of(Math.subtractExact(cents, other.cents));
    }

    public boolean isNegative() {
        return cents < 0;
    }

    @Override
    public int compareTo(MoneyFen other) {
        return Long.compare(cents, other.cents);
    }

    @Override
    public String toString() {
        return cents + "分";
    }

}
