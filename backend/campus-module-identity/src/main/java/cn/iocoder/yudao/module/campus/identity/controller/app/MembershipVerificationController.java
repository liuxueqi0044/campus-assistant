package cn.iocoder.yudao.module.campus.identity.controller.app;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.common.api.CampusApiContract;
import cn.iocoder.yudao.module.campus.common.api.CampusResponse;
import cn.iocoder.yudao.module.campus.identity.application.MembershipService;
import cn.iocoder.yudao.module.campus.identity.application.VerificationApplicationService;
import cn.iocoder.yudao.module.campus.identity.application.verification.SubmitVerificationCommand;
import cn.iocoder.yudao.module.campus.identity.controller.CampusHttpRequestValidator;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.MembershipResponse;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.SubmitVerificationRequest;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.VerificationApplicationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated member verification and self-membership boundary. */
@Tag(name = "校园 - 成员认证")
@RestController
@TenantIgnore
@RequestMapping("/campus/v1")
@RequiredArgsConstructor
public class MembershipVerificationController {

    private static final String SELF_CHECK_ACTION = "SELF_CHECK";

    private final MembershipService membershipService;
    private final VerificationApplicationService verificationApplicationService;

    @PostMapping("/verifications")
    @Operation(summary = "提交校园身份认证")
    public CampusResponse<VerificationApplicationResponse> submit(@RequestBody @Valid SubmitVerificationRequest request,
                                                                    @RequestHeader(value = CampusApiContract.HEADER_IDEMPOTENCY_KEY,
                                                                            required = false) String idempotencyKey) {
        long userId = CampusHttpRequestValidator.requiredCurrentUserId();
        SubmitVerificationCommand command = new SubmitVerificationCommand(
                CampusHttpRequestValidator.positiveId(request.getSchoolId(), "schoolId"),
                CampusHttpRequestValidator.positiveId(request.getCampusId(), "campusId"),
                request.getMethod().name(), request.getEvidenceRefs(),
                CampusHttpRequestValidator.requiredIdempotencyKey(idempotencyKey));
        return CampusResponse.success(VerificationApplicationResponse.from(
                verificationApplicationService.submit(userId, command)));
    }

    @GetMapping("/membership")
    @Operation(summary = "检查当前用户的校园成员资格")
    public CampusResponse<MembershipResponse> membership(@RequestParam String schoolId, @RequestParam String campusId) {
        long resultUserId = CampusHttpRequestValidator.requiredCurrentUserId();
        return CampusResponse.success(MembershipResponse.from(membershipService.check(resultUserId,
                CampusHttpRequestValidator.positiveId(schoolId, "schoolId"),
                CampusHttpRequestValidator.positiveId(campusId, "campusId"), SELF_CHECK_ACTION)));
    }
}
