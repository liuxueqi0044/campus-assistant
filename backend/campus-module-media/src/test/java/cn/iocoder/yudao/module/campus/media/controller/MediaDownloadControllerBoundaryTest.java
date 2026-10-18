package cn.iocoder.yudao.module.campus.media.controller;

import cn.iocoder.yudao.module.campus.identity.api.ActorRef;
import cn.iocoder.yudao.module.campus.identity.api.ActorType;
import cn.iocoder.yudao.module.campus.identity.controller.CampusPlatformPrincipalGuard;
import cn.iocoder.yudao.module.campus.media.application.DownloadAuthorizationService;
import cn.iocoder.yudao.module.campus.media.application.MediaDeliveryIssueResult;
import cn.iocoder.yudao.module.campus.media.controller.admin.MediaDownloadAdminController;
import cn.iocoder.yudao.module.campus.media.controller.admin.vo.AdminMediaDownloadRequest;
import cn.iocoder.yudao.module.campus.media.controller.app.MediaDownloadController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaDownloadControllerBoundaryTest {

    private static final MediaDeliveryIssueResult ISSUE = new MediaDeliveryIssueResult(
            "delivery-token", Instant.parse("2026-09-18T00:01:00Z"), 3L, 9L);

    @Test
    void appEndpointsBuildMemberActorFromAuthenticatedServerIdentity() throws Exception {
        DownloadAuthorizationService authorization = mock(DownloadAuthorizationService.class);
        MediaDownloadResponseWriter writer = mock(MediaDownloadResponseWriter.class);
        when(authorization.issue(new ActorRef(ActorType.MEMBER, 101L), 11L, null, null)).thenReturn(ISSUE);

        CampusPlatformPrincipalGuard guard = mock(CampusPlatformPrincipalGuard.class);
        when(guard.requiredMemberId()).thenReturn(101L);
        MediaDownloadController controller = new MediaDownloadController(authorization, writer, guard);

        controller.issue("11");
        controller.download("delivery-token", new MockHttpServletResponse());

        verify(authorization).issue(new ActorRef(ActorType.MEMBER, 101L), 11L, null, null);
        verify(writer).write(eq(new ActorRef(ActorType.MEMBER, 101L)), eq("delivery-token"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminEndpointsBuildOperatorActorAndKeepCaseContextInRequestOnlyForAuthorization() throws Exception {
        DownloadAuthorizationService authorization = mock(DownloadAuthorizationService.class);
        MediaDownloadResponseWriter writer = mock(MediaDownloadResponseWriter.class);
        when(authorization.issue(new ActorRef(ActorType.OPERATOR, 303L), 11L,
                "case-9", "operator review")).thenReturn(ISSUE);
        AdminMediaDownloadRequest request = new AdminMediaDownloadRequest();
        request.setCaseReference("case-9");
        request.setReason("operator review");

        CampusPlatformPrincipalGuard guard = mock(CampusPlatformPrincipalGuard.class);
        when(guard.requiredOperatorId()).thenReturn(303L);
        MediaDownloadAdminController controller = new MediaDownloadAdminController(authorization, writer, guard);

        controller.issue("11", request);
        controller.download("delivery-token", new MockHttpServletResponse());

        verify(authorization).issue(new ActorRef(ActorType.OPERATOR, 303L), 11L,
                "case-9", "operator review");
        verify(writer).write(eq(new ActorRef(ActorType.OPERATOR, 303L)), eq("delivery-token"),
                org.mockito.ArgumentMatchers.any());
    }
}
