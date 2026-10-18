package cn.iocoder.yudao.module.campus.media.controller.app;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.common.api.CampusResponse;
import cn.iocoder.yudao.module.campus.identity.api.ActorRef;
import cn.iocoder.yudao.module.campus.identity.api.ActorType;
import cn.iocoder.yudao.module.campus.identity.controller.CampusHttpRequestValidator;
import cn.iocoder.yudao.module.campus.identity.controller.CampusPlatformPrincipalGuard;
import cn.iocoder.yudao.module.campus.media.application.DownloadAuthorizationService;
import cn.iocoder.yudao.module.campus.media.controller.MediaDownloadResponseWriter;
import cn.iocoder.yudao.module.campus.media.controller.app.vo.MediaDownloadGrantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Tag(name = "校园 - 私有媒体")
@RestController
@TenantIgnore
@RequestMapping("/campus/v1/media")
@RequiredArgsConstructor
public class MediaDownloadController {

    private final DownloadAuthorizationService authorizationService;
    private final MediaDownloadResponseWriter responseWriter;
    private final CampusPlatformPrincipalGuard principalGuard;

    @PostMapping("/assets/{assetId}/downloads")
    @Operation(summary = "为本人私有材料签发短期代理读取令牌")
    public CampusResponse<MediaDownloadGrantResponse> issue(@PathVariable String assetId) {
        ActorRef actor = currentMember();
        return CampusResponse.success(MediaDownloadGrantResponse.from(authorizationService.issue(actor,
                CampusHttpRequestValidator.positiveId(assetId, "assetId"), null, null)));
    }

    @GetMapping("/downloads/{deliveryToken}")
    @Operation(summary = "通过短期令牌代理读取私有材料")
    public void download(@PathVariable String deliveryToken, HttpServletResponse response) throws IOException {
        responseWriter.write(currentMember(), deliveryToken, response);
    }

    private ActorRef currentMember() {
        return new ActorRef(ActorType.MEMBER, principalGuard.requiredMemberId());
    }
}
