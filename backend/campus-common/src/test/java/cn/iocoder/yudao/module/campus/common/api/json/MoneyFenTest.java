package cn.iocoder.yudao.module.campus.common.api.json;

import cn.iocoder.yudao.module.campus.common.api.CampusApiContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M00.02 验收（A 级）：金额只能是整数分，小数与字符串都必须失败。
 */
class MoneyFenTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void integerMoney_shouldRoundTripAsPlainInteger() throws Exception {
        MoneyHolder holder = objectMapper.readValue("{\"amountFen\":1234}", MoneyHolder.class);

        assertThat(holder.getAmountFen().getCents()).isEqualTo(1234L);
        assertThat(objectMapper.writeValueAsString(holder)).isEqualTo("{\"amountFen\":1234}");
    }

    @Test
    void decimalMoney_shouldFailInsteadOfBeingSilentlyTruncated() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"amountFen\":12.5}", MoneyHolder.class))
                .hasRootCauseInstanceOf(CampusMoneyFormatException.class);
    }

    @Test
    void stringMoney_shouldFailSoThatUnitIsNeverAmbiguous() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"amountFen\":\"1234\"}", MoneyHolder.class))
                .hasRootCauseInstanceOf(CampusMoneyFormatException.class);
    }

    @Test
    void money_shouldBehaveAsValueTypeWithoutFloatingPoint() {
        assertThat(MoneyFen.ofYuan(12).plus(MoneyFen.of(34))).isEqualTo(MoneyFen.of(1234));
        assertThat(MoneyFen.of(100).minus(MoneyFen.of(150))).isEqualTo(MoneyFen.of(-50));
        assertThat(MoneyFen.of(-1).isNegative()).isTrue();
        assertThat(MoneyFen.ZERO.getCents()).isZero();
        assertThat(CampusApiContract.MONEY_UNIT).isEqualTo("CNY_FEN");
    }

    /** 用例内部 DTO，模拟业务响应对金额字段的使用方式 */
    static class MoneyHolder {

        private MoneyFen amountFen;

        public MoneyFen getAmountFen() {
            return amountFen;
        }

        public void setAmountFen(MoneyFen amountFen) {
            this.amountFen = amountFen;
        }

    }

}
