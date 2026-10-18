package cn.iocoder.yudao.module.campus.media.controller;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.api.ActorRef;
import cn.iocoder.yudao.module.campus.identity.api.ActorType;
import cn.iocoder.yudao.module.campus.media.api.MediaStoragePort;
import cn.iocoder.yudao.module.campus.media.api.StoredObjectMetadata;
import cn.iocoder.yudao.module.campus.media.application.AuthorizedMediaObject;
import cn.iocoder.yudao.module.campus.media.application.DownloadAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaDownloadResponseWriterTest {

    private static final ActorRef ACTOR = new ActorRef(ActorType.MEMBER, 101);
    private static final String TOKEN = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmno";

    @Test
    void proxiesValidatedBytesWithNoStoreAndSafeAttachmentHeaders() throws Exception {
        DownloadAuthorizationService authorization = mock(DownloadAuthorizationService.class);
        MediaStoragePort storage = mock(MediaStoragePort.class);
        byte[] bytes = "proof".getBytes(StandardCharsets.UTF_8);
        when(authorization.authorizeOpen(ACTOR, TOKEN)).thenReturn(
                new AuthorizedMediaObject("private/proof/11", "image/png", bytes.length, "校园卡.png"));
        when(storage.stat("private/proof/11")).thenReturn(new StoredObjectMetadata(bytes.length));
        when(storage.open("private/proof/11")).thenReturn(new ByteArrayInputStream(bytes));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new MediaDownloadResponseWriter(authorization, storage).write(ACTOR, TOKEN, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Content-Disposition")).contains("attachment").contains("UTF-8");
        assertThat(response.getContentAsByteArray()).containsExactly(bytes);
    }

    @Test
    void refusesStorageWhoseLengthNoLongerMatchesValidatedFacts() {
        DownloadAuthorizationService authorization = mock(DownloadAuthorizationService.class);
        MediaStoragePort storage = mock(MediaStoragePort.class);
        when(authorization.authorizeOpen(ACTOR, TOKEN)).thenReturn(
                new AuthorizedMediaObject("private/proof/11", "image/png", 5, "card.png"));
        when(storage.stat("private/proof/11")).thenReturn(new StoredObjectMetadata(4));

        assertThatThrownBy(() -> new MediaDownloadResponseWriter(authorization, storage)
                .write(ACTOR, TOKEN, new MockHttpServletResponse()))
                .isInstanceOfSatisfying(CampusApiException.class,
                        error -> assertThat(error.getSpec()).isEqualTo(CampusErrorCodes.MEDIA_UNAVAILABLE));
        verify(storage, never()).open("private/proof/11");
    }

    @Test
    void refusesMidStreamStorageFailureBeforeCommittingHeadersOrBody() {
        DownloadAuthorizationService authorization = mock(DownloadAuthorizationService.class);
        MediaStoragePort storage = mock(MediaStoragePort.class);
        when(authorization.authorizeOpen(ACTOR, TOKEN)).thenReturn(
                new AuthorizedMediaObject("private/proof/11", "image/png", 5, "card.png"));
        when(storage.stat("private/proof/11")).thenReturn(new StoredObjectMetadata(5));
        when(storage.open("private/proof/11")).thenReturn(new InputStream() {
            private int read;

            @Override
            public int read() throws IOException {
                if (read++ < 2) {
                    return 'x';
                }
                throw new IOException("fixture storage interruption");
            }
        });
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> new MediaDownloadResponseWriter(authorization, storage)
                .write(ACTOR, TOKEN, response))
                .isInstanceOfSatisfying(CampusApiException.class,
                        error -> assertThat(error.getSpec()).isEqualTo(CampusErrorCodes.MEDIA_UNAVAILABLE));
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getHeader("Cache-Control")).isNull();
    }
}
