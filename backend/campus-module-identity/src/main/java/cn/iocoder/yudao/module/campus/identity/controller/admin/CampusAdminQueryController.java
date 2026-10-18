package cn.iocoder.yudao.module.campus.identity.controller.admin;

import cn.iocoder.yudao.module.campus.common.api.CampusResponse;
import cn.iocoder.yudao.module.campus.identity.application.CampusAdminQueryService;
import cn.iocoder.yudao.module.campus.identity.controller.CampusPlatformPrincipalGuard;
import cn.iocoder.yudao.module.campus.identity.controller.admin.vo.CampusAdminContextResponse;
import cn.iocoder.yudao.module.campus.identity.controller.admin.vo.VerificationDetailResponse;
import cn.iocoder.yudao.module.campus.identity.controller.admin.vo.VerificationQueueResponse;
import cn.iocoder.yudao.module.campus.identity.controller.CampusHttpRequestValidator;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@TenantIgnore
@RequestMapping("/campus/v1")
@RequiredArgsConstructor
public class CampusAdminQueryController {
    private final CampusAdminQueryService queryService;
    private final CampusPlatformPrincipalGuard principalGuard;

    @GetMapping("/context")
    public CampusResponse<CampusAdminContextResponse> context() {
        return CampusResponse.success(queryService.context(principalGuard.requiredOperatorId()));
    }

    @GetMapping("/verifications")
    public CampusResponse<VerificationQueueResponse> queue(@RequestParam String schoolId,
                                                            @RequestParam(required = false) String campusId,
                                                            @RequestParam(required = false, defaultValue = "PENDING") String status,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int pageSize) {
        return CampusResponse.success(queryService.queue(principalGuard.requiredOperatorId(), requiredId(schoolId, "schoolId"),
                optionalId(campusId, "campusId"), status, page, pageSize));
    }

    @GetMapping("/verifications/{id}")
    public CampusResponse<VerificationDetailResponse> detail(@PathVariable String id) {
        return CampusResponse.success(queryService.detail(principalGuard.requiredOperatorId(),
                CampusHttpRequestValidator.positiveId(id, "applicationId")));
    }

    private static Long optionalId(String value, String field) {
        return value == null || value.isBlank() ? null : CampusHttpRequestValidator.positiveId(value, field);
    }

    private static Long requiredId(String value, String field) {
        return CampusHttpRequestValidator.positiveId(value, field);
    }
}
