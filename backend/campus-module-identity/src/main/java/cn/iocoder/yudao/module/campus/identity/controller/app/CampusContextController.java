package cn.iocoder.yudao.module.campus.identity.controller.app;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.common.api.CampusResponse;
import cn.iocoder.yudao.module.campus.identity.application.CampusContextService;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.CampusContextResponse;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.CampusContextUpdateRequest;
import cn.iocoder.yudao.module.campus.identity.controller.app.vo.SchoolDirectoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous-safe M01.02 public browsing APIs. */
@Tag(name = "校园 - 学校、校区与浏览上下文")
@RestController
@TenantIgnore // Public directory/context is a campus scope, not a SaaS tenant scope.
// yudao.web.app-api.controller applies the /app-api prefix to this package.
// Keep only the module-relative portion here; repeating /app-api makes both MVC
// and the upstream @PermitAll scanner register /app-api/app-api/... instead.
@RequestMapping("/campus/v1")
@RequiredArgsConstructor
public class CampusContextController {

    private final CampusContextService campusContextService;

    @GetMapping("/schools")
    @PermitAll
    @Operation(summary = "获得启用的学校与校区公开目录")
    public CampusResponse<SchoolDirectoryResponse> listSchools(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor) {
        return CampusResponse.success(campusContextService.listSchools(keyword, cursor));
    }

    @GetMapping("/context")
    @PermitAll
    @Operation(summary = "获得当前公开浏览上下文")
    public CampusResponse<CampusContextResponse> getContext() {
        return CampusResponse.success(campusContextService.getContext(SecurityFrameworkUtils.getLoginUserId()));
    }

    @PutMapping("/context")
    @PermitAll
    @Operation(summary = "校验并保存当前浏览校区；匿名调用仅回显验证结果")
    public CampusResponse<CampusContextResponse> updateContext(@RequestBody @Valid CampusContextUpdateRequest request) {
        return CampusResponse.success(campusContextService.updateContext(SecurityFrameworkUtils.getLoginUserId(),
                request.getSchoolId(), request.getCampusId()));
    }
}
