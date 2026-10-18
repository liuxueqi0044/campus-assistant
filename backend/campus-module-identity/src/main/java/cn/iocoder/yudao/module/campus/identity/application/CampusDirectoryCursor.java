package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorDetail;
import cn.iocoder.yudao.module.campus.common.api.CampusFieldError;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Opaque, keyset cursor for the public school directory. */
final class CampusDirectoryCursor {

    static final CampusDirectoryCursor START = new CampusDirectoryCursor(Integer.MIN_VALUE, 0L);

    private final int sortOrder;
    private final long schoolId;

    private CampusDirectoryCursor(int sortOrder, long schoolId) {
        this.sortOrder = sortOrder;
        this.schoolId = schoolId;
    }

    static CampusDirectoryCursor decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return START;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new CampusDirectoryCursor(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    static String encode(int sortOrder, long schoolId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((sortOrder + ":" + schoolId).getBytes(StandardCharsets.UTF_8));
    }

    int sortOrder() {
        return sortOrder;
    }

    long schoolId() {
        return schoolId;
    }

    private static CampusApiException invalidCursor() {
        return new CampusApiException(CampusErrorCodes.REQUEST_INVALID,
                CampusErrorDetail.of(CampusErrorCodes.REQUEST_INVALID.getRetryAction())
                        .withFieldErrors(List.of(new CampusFieldError("cursor", "游标格式无效"))));
    }
}
