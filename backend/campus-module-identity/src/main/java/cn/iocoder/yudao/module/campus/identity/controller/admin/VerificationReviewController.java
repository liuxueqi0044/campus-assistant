package cn.iocoder.yudao.module.campus.identity.controller.admin;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.common.api.CampusApiContract;
import cn.iocoder.yudao.module.campus.common.api.CampusResponse;
import cn.iocoder.yudao.module.campus.identity.application.VerificationApplicationService;
import cn.iocoder.yudao.module.campus.identity.application.verification.ReviewVerificationCommand;
import cn.iocoder.yudao.module.campus.identity.controller.CampusHttpRequestValidator;
import cn.iocoder.yudao.module.campus.identity.controller.CampusPlatformPrincipalGuard;
import cn.iocoder.yudao.module.campus.identity.controller.admin.vo.ReviewVerificationRequest;
import cn.iocoder.yudao.module.campus.identity.controller.admin.vo.VerificationReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated administrator review command; school scope is enforced by the application authorization port. */
@Tag(name = "校园 - 认证审核")
@RestController
@TenantIgnore
@RequestMapping("/campus/v1")
@RequiredArgsConstructor
public class VerificationReviewController {

    private final VerificationApplicationService verificationApplicationService;
    private final CampusPlatformPrincipalGuard principalGuard;

    @PostMapping("/verifications/{id}/review")
    @Operation(summary = "审核校园身份认证申请")
    public CampusResponse<VerificationReviewResponse> review(@PathVariable String id,
                                                              @RequestBody @Valid ReviewVerificationRequest request,
                                                              @RequestHeader(value = CampusApiContract.HEADER_IDEMPOTENCY_KEY,
                                                                      required = false) String idempotencyKey) {
        long operatorId = principalGuard.requiredOperatorId();
        ReviewVerificationCommand command = new ReviewVerificationCommand(
                CampusHttpRequestValidator.positiveId(id, "id"), request.getDecision(), request.getReason(),
                request.getExpectedVersion(), CampusHttpRequestValidator.requiredIdempotencyKey(idempotencyKey));
        return CampusResponse.success(VerificationReviewResponse.from(
                verificationApplicationService.review(operatorId, command)));
    }
}
