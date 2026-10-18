package cn.iocoder.yudao.module.campus.common.api;

import cn.iocoder.yudao.module.campus.common.api.json.MoneyFen;
import cn.iocoder.yudao.module.campus.common.transaction.TransactionRetryExhaustedException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * M00.02 验收用探针控制器：把契约要求（信封、字符串 ID、整数分金额、分页上限、409、兜底 500）
 * 变成可通过 HTTP 断言的真实端点。
 *
 * <p>只存在于测试源码，不会进入交付 jar；真实业务端点由 M01–M11 按同一规则实现。
 */
@RestController
@RequestMapping(CampusApiContract.APP_API_PREFIX + "/contract-probe")
public class CampusContractProbeController {

    /** 固定时间，避免用例依赖真实时钟 */
    public static final OffsetDateTime FIXED_TIME =
            OffsetDateTime.of(2026, 9, 17, 10, 15, 30, 0, ZoneOffset.ofHours(8));

    private static final String SCHOOL_S1 = "S1";

    @GetMapping("/orders/{id}")
    public CampusResponse<CampusProbeOrderRespVO> getOrder(@PathVariable("id") String id) {
        return CampusResponse.success(new CampusProbeOrderRespVO(id, SCHOOL_S1, MoneyFen.of(1234), FIXED_TIME));
    }

    @PostMapping("/orders")
    public CampusResponse<CampusProbeOrderRespVO> createOrder(
            @Valid @RequestBody CampusProbeOrderCreateReqVO request) {
        return CampusResponse.success(new CampusProbeOrderRespVO(request.getBuyerId(), request.getSchoolId(),
                request.getAmountFen(), request.getExpectedAt() != null ? request.getExpectedAt() : FIXED_TIME));
    }

    @GetMapping("/orders")
    public CampusResponse<CampusPageResult<CampusProbeOrderRespVO>> pageOrders(CampusPageParam page) {
        page.enforceLimits();
        List<CampusProbeOrderRespVO> orders = List.of(
                new CampusProbeOrderRespVO("1", SCHOOL_S1, MoneyFen.of(100), FIXED_TIME));
        return CampusResponse.success(CampusPageResult.of(orders, 1L, page));
    }

    @GetMapping("/version-conflict")
    public CampusResponse<CampusProbeOrderRespVO> versionConflict() {
        throw new CampusApiException(CampusErrorCodes.VERSION_CONFLICT,
                CampusErrorDetail.of(CampusRetryAction.USER_FIX).withConflict("7", "ACCEPTED"));
    }

    @GetMapping("/retryable-conflict")
    public CampusResponse<CampusProbeOrderRespVO> retryableConflict() {
        throw new TransactionRetryExhaustedException("internal-operation", 3,
                new IllegalStateException("database detail must stay internal"));
    }

    @GetMapping("/boom")
    public CampusResponse<CampusProbeOrderRespVO> boom() {
        throw new IllegalStateException("内部信息，不应外泄：jdbc:mysql://10.0.0.1:3306/campus");
    }

    @GetMapping("/hidden-orders/{id}")
    public CampusResponse<CampusProbeOrderRespVO> hiddenOrder(@PathVariable("id") String id) {
        // 无权限或不存在都用同一 404，避免通过状态码探测资源是否存在
        throw new CampusApiException(CampusErrorCodes.RESOURCE_NOT_FOUND);
    }

}
