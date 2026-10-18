package cn.iocoder.yudao.module.campus.media.controller.app;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.campus.common.api.CampusApiContract;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.common.api.CampusResponse;
import cn.iocoder.yudao.module.campus.identity.controller.CampusPlatformPrincipalGuard;
import cn.iocoder.yudao.module.campus.media.application.MediaUploadService;
import cn.iocoder.yudao.module.campus.media.application.MediaCompletionResult;
import cn.iocoder.yudao.module.campus.media.application.MediaCompletionService;
import cn.iocoder.yudao.module.campus.media.controller.app.vo.CompleteMediaRequest;
import cn.iocoder.yudao.module.campus.media.controller.app.vo.MediaCompletionResponse;
import cn.iocoder.yudao.module.campus.media.application.UploadTicketCommand;
import cn.iocoder.yudao.module.campus.media.application.UploadTicketService;
import cn.iocoder.yudao.module.campus.media.controller.app.vo.UploadContentResponse;
import cn.iocoder.yudao.module.campus.media.controller.app.vo.UploadTicketRequest;
import cn.iocoder.yudao.module.campus.media.controller.app.vo.UploadTicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Tag(name = "校园 - 私有媒体")
@RestController
@TenantIgnore
@RequestMapping("/campus/v1/media")
@RequiredArgsConstructor
public class MediaUploadController {

    private final UploadTicketService ticketService;
    private final MediaUploadService uploadService;
    private final MediaCompletionService completionService;
    private final CampusPlatformPrincipalGuard principalGuard;

    @PostMapping("/upload-tickets")
    @Operation(summary = "申请受限上传票据")
    public CampusResponse<UploadTicketResponse> issue(@RequestBody @Valid UploadTicketRequest request,
                                                       @RequestHeader(value = CampusApiContract.HEADER_IDEMPOTENCY_KEY,
                                                               required = false) String idempotencyKey) {
        long userId = principalGuard.requiredMemberId();
        return CampusResponse.success(UploadTicketResponse.from(ticketService.issue(userId,
                new UploadTicketCommand(request.getPurpose(), request.getFileName(), request.getSize(),
                        request.getMime(), idempotencyKey))));
    }

    @PutMapping("/assets/{assetId}/content")
    @Operation(summary = "上传到开发环境私有存储")
    public CampusResponse<UploadContentResponse> upload(@PathVariable String assetId,
                                                         @RequestHeader("X-Upload-Token") String uploadToken,
                                                         HttpServletRequest request) throws IOException {
        long userId = principalGuard.requiredMemberId();
        long parsedAssetId = positiveId(assetId);
        return CampusResponse.success(UploadContentResponse.from(uploadService.upload(userId, parsedAssetId,
                uploadToken, request.getContentType(), request.getContentLengthLong(), request.getInputStream())));
    }

    @PostMapping("/assets/{assetId}/complete")
    @Operation(summary = "验证已上传的私有媒体")
    public CampusResponse<MediaCompletionResponse> complete(@PathVariable String assetId,
                                                             @RequestBody @Valid CompleteMediaRequest request) {
        long userId = principalGuard.requiredMemberId();
        MediaCompletionResult result = completionService.complete(userId, positiveId(assetId),
                request.getGeneration());
        if ("REJECTED".equals(result.status())) {
            throw new CampusApiException(CampusErrorCodes.FILE_INVALID);
        }
        return CampusResponse.success(MediaCompletionResponse.from(result));
    }

    private static long positiveId(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
                throw new NumberFormatException("not a canonical positive id");
            }
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID);
        }
    }
}
