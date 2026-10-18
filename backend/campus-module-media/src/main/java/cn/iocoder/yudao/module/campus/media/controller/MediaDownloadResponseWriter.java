package cn.iocoder.yudao.module.campus.media.controller;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.api.ActorRef;
import cn.iocoder.yudao.module.campus.media.api.MediaStorageException;
import cn.iocoder.yudao.module.campus.media.api.MediaStoragePort;
import cn.iocoder.yudao.module.campus.media.application.AuthorizedMediaObject;
import cn.iocoder.yudao.module.campus.media.application.DownloadAuthorizationService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Writes one already-authorized private object without exposing its storage address. */
@Component
@RequiredArgsConstructor
public class MediaDownloadResponseWriter {

    private static final int BUFFER_SIZE = 8192;

    private final DownloadAuthorizationService authorizationService;
    private final MediaStoragePort storage;

    public void write(ActorRef actor, String deliveryToken, HttpServletResponse response) throws IOException {
        AuthorizedMediaObject object = authorizationService.authorizeOpen(actor, deliveryToken);
        byte[] bytes;
        try {
            if (storage.stat(object.objectKey()).size() != object.actualSize()) {
                throw unavailable();
            }
            try (InputStream input = storage.open(object.objectKey())) {
                bytes = readExact(input, object.actualSize());
            }
        } catch (MediaStorageException | IllegalArgumentException | IOException exception) {
            throw unavailable();
        }
        configureResponse(response, object);
        response.getOutputStream().write(bytes);
    }

    private static void configureResponse(HttpServletResponse response, AuthorizedMediaObject object) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(object.actualMime());
        response.setContentLengthLong(object.actualSize());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", ContentDisposition.attachment()
                .filename(object.originalFilename(), StandardCharsets.UTF_8).build().toString());
    }

    private static byte[] readExact(InputStream input, long expectedSize) throws IOException {
        if (expectedSize <= 0 || expectedSize > Integer.MAX_VALUE) {
            throw new IOException("invalid validated private media length");
        }
        byte[] bytes = new byte[(int) expectedSize];
        byte[] buffer = new byte[BUFFER_SIZE];
        int offset = 0;
        int remaining = bytes.length;
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new IOException("private media object ended before its validated length");
            }
            System.arraycopy(buffer, 0, bytes, offset, read);
            offset += read;
            remaining -= read;
        }
        if (input.read() != -1) {
            throw new IOException("private media object exceeded its validated length");
        }
        return bytes;
    }

    private static CampusApiException unavailable() {
        return new CampusApiException(CampusErrorCodes.MEDIA_UNAVAILABLE);
    }
}
