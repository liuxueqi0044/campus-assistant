package cn.iocoder.yudao.module.campus.identity.controller;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorDetail;
import cn.iocoder.yudao.module.campus.common.api.CampusFieldError;
import cn.iocoder.yudao.module.campus.common.idempotency.IdempotentCommand;

import java.util.List;

/** Keeps HTTP-only identity and identifier checks out of the application command boundary. */
public final class CampusHttpRequestValidator {

    private CampusHttpRequestValidator() {
    }

    public static long requiredCurrentUserId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            throw new CampusApiException(CampusErrorCodes.AUTH_REQUIRED);
        }
        return userId;
    }

    public static long positiveId(String raw, String field) {
        try {
            if (raw == null || !raw.matches("[1-9][0-9]*")) {
                throw new NumberFormatException("id is not a positive integer string");
            }
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw invalid(field, "标识字段必须是正整数字符串");
        }
    }

    public static String requiredIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > IdempotentCommand.KEY_MAX_LENGTH) {
            throw invalid("Idempotency-Key", "幂等键不能为空且长度不能超过 "
                    + IdempotentCommand.KEY_MAX_LENGTH);
        }
        return key.trim();
    }

    private static CampusApiException invalid(String field, String reason) {
        return new CampusApiException(CampusErrorCodes.REQUEST_INVALID,
                CampusErrorDetail.of(CampusErrorCodes.REQUEST_INVALID.getRetryAction())
                        .withFieldErrors(List.of(new CampusFieldError(field, reason))));
    }
}
