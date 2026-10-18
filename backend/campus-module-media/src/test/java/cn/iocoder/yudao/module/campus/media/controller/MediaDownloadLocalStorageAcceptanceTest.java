package cn.iocoder.yudao.module.campus.media.controller;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.api.ActorRef;
import cn.iocoder.yudao.module.campus.identity.api.ActorType;
import cn.iocoder.yudao.module.campus.media.application.AuthorizedMediaObject;
import cn.iocoder.yudao.module.campus.media.application.DownloadAuthorizationService;
import cn.iocoder.yudao.module.campus.media.infrastructure.LocalPrivateStorageAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaDownloadLocalStorageAcceptanceTest {

    private static final ActorRef ACTOR = new ActorRef(ActorType.MEMBER, 101L);
    private static final String TOKEN = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmno";

    @TempDir
    Path root;

    @Test
    void streamsRealPrivateBytesWithDownloadSafetyHeaders() throws Exception {
        LocalPrivateStorageAdapter storage = new LocalPrivateStorageAdapter(root.toString());
        byte[] bytes = "真实私有证明".getBytes(StandardCharsets.UTF_8);
        storage.putImmutable("private/proof/11", new ByteArrayInputStream(bytes), bytes.length);
        DownloadAuthorizationService authorization = mock(DownloadAuthorizationService.class);
        when(authorization.authorizeOpen(ACTOR, TOKEN)).thenReturn(
                new AuthorizedMediaObject("private/proof/11", "image/png", bytes.length, "校园卡.png"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new MediaDownloadResponseWriter(authorization, storage).write(ACTOR, TOKEN, response);

        assertThat(response.getContentAsByteArray()).containsExactly(bytes);
        assertThat(response.getContentLength()).isEqualTo(bytes.length);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Content-Disposition")).contains("attachment").contains("UTF-8");
    }

    @Test
    void missingPrivateObjectFailsClosedBeforeAResponseBodyIsWritten() throws Exception {
        LocalPrivateStorageAdapter storage = new LocalPrivateStorageAdapter(root.toString());
        DownloadAuthorizationService authorization = mock(DownloadAuthorizationService.class);
        when(authorization.authorizeOpen(ACTOR, TOKEN)).thenReturn(
                new AuthorizedMediaObject("private/proof/missing", "image/png", 5L, "card.png"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> new MediaDownloadResponseWriter(authorization, storage)
                .write(ACTOR, TOKEN, response))
                .isInstanceOfSatisfying(CampusApiException.class,
                        error -> assertThat(error.getSpec()).isEqualTo(CampusErrorCodes.MEDIA_UNAVAILABLE));
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void changedPrivateObjectLengthFailsClosedAgainstTheValidatedSnapshot() throws Exception {
        LocalPrivateStorageAdapter storage = new LocalPrivateStorageAdapter(root.toString());
        storage.putImmutable("private/proof/11", new ByteArrayInputStream(new byte[]{1, 2, 3, 4}), 4);
        DownloadAuthorizationService authorization = mock(DownloadAuthorizationService.class);
        when(authorization.authorizeOpen(ACTOR, TOKEN)).thenReturn(
                new AuthorizedMediaObject("private/proof/11", "image/png", 5L, "card.png"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> new MediaDownloadResponseWriter(authorization, storage)
                .write(ACTOR, TOKEN, response))
                .isInstanceOfSatisfying(CampusApiException.class,
                        error -> assertThat(error.getSpec()).isEqualTo(CampusErrorCodes.MEDIA_UNAVAILABLE));
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
}
