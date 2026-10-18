package cn.iocoder.yudao.module.campus.common.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M00.02 验收（A 级）：分页默认值与硬上限冻结。
 */
class CampusPageParamTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaults_shouldBeFrozenInContract() {
        CampusPageParam param = new CampusPageParam();

        assertThat(param.getPageNo()).isEqualTo(CampusPageParam.DEFAULT_PAGE_NO);
        assertThat(param.getPageSize()).isEqualTo(CampusPageParam.DEFAULT_PAGE_SIZE);
        assertThat(CampusPageParam.MAX_PAGE_SIZE).isEqualTo(100);
    }

    @Test
    void beanValidation_shouldRejectPageSizeAboveCapAndNonPositivePageNo() {
        CampusPageParam param = new CampusPageParam();
        param.setPageSize(CampusPageParam.MAX_PAGE_SIZE + 1);
        param.setPageNo(0);

        Set<ConstraintViolation<CampusPageParam>> violations = validator.validate(param);

        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("pageSize", "pageNo");
    }

    @Test
    void enforceLimits_shouldThrowFrozenCodeWhenValidationLayerIsSkipped() {
        CampusPageParam param = new CampusPageParam();
        param.setPageSize(CampusPageParam.MAX_PAGE_SIZE + 1);

        assertThatThrownBy(param::enforceLimits)
                .isInstanceOf(CampusApiException.class)
                .extracting(ex -> ((CampusApiException) ex).getSpec().getName())
                .isEqualTo(CampusErrorCodes.PAGE_SIZE_EXCEEDED.getName());
    }

    @Test
    void enforceLimits_shouldAcceptBoundaryValues() {
        CampusPageParam param = new CampusPageParam();
        param.setPageSize(CampusPageParam.MAX_PAGE_SIZE);

        assertThat(param.enforceLimits()).isSameAs(param);
    }

}
