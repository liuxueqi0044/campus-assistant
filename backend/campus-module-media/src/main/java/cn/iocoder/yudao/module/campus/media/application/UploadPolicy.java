package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.media.domain.MediaPurpose;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

@Component
public class UploadPolicy {

    public PolicyDecision requireAllowed(String rawPurpose, String rawFileName, long size, String rawMime) {
        MediaPurpose purpose;
        try {
            purpose = MediaPurpose.valueOf(required(rawPurpose, "purpose").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new CampusApiException(CampusErrorCodes.UPLOAD_PURPOSE_DENIED);
        }
        String fileName = normalizeFileName(rawFileName);
        String mime = required(rawMime, "mime").toLowerCase(Locale.ROOT);
        if (size <= 0) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID);
        }
        if (size > purpose.maxBytes()) {
            throw new CampusApiException(CampusErrorCodes.FILE_SIZE_EXCEEDED);
        }
        if (!purpose.accepts(mime)) {
            throw new CampusApiException(CampusErrorCodes.UPLOAD_PURPOSE_DENIED);
        }
        return new PolicyDecision(purpose, fileName, size, mime);
    }

    private static String normalizeFileName(String value) {
        String fileName = required(value, "fileName");
        if (fileName.length() > 255 || fileName.contains("/") || fileName.contains("\\")
                || fileName.indexOf('\0') >= 0) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID);
        }
        return fileName;
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new CampusApiException(CampusErrorCodes.REQUEST_INVALID);
        }
        return normalized;
    }

    public record PolicyDecision(MediaPurpose purpose, String fileName, long size, String mime) {
    }
}
