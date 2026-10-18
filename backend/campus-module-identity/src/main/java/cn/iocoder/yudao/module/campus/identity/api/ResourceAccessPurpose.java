package cn.iocoder.yudao.module.campus.identity.api;

import java.util.Objects;

/** Minimal access context; operator access to private data can require both case reference and reason. */
public record ResourceAccessPurpose(String purpose, String caseReference, String reason) {

    public ResourceAccessPurpose {
        purpose = require(purpose, "purpose", 64, true);
        caseReference = require(caseReference, "caseReference", 128, false);
        reason = require(reason, "reason", 500, false);
    }

    public static ResourceAccessPurpose ownerUse(String purpose) {
        return new ResourceAccessPurpose(purpose, null, null);
    }

    private static String require(String value, String name, int maxLength, boolean mandatory) {
        if (value == null) {
            if (mandatory) {
                throw new NullPointerException(name);
            }
            return null;
        }
        String result = value.trim();
        if (result.length() > maxLength || mandatory && result.isEmpty()) {
            throw new IllegalArgumentException(name + " has invalid length");
        }
        return result.isEmpty() ? null : result;
    }
}
