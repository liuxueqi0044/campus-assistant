package cn.iocoder.yudao.module.campus.identity.api;

import java.util.Objects;

/** Opaque resource identity; it deliberately contains no private resource payload. */
public record ResourceRef(String type, String id, Long schoolId) {

    public ResourceRef {
        type = requireToken(type, "type", 64);
        id = requireToken(id, "id", 128);
        if (schoolId != null && schoolId <= 0) {
            throw new IllegalArgumentException("schoolId must be positive");
        }
    }

    private static String requireToken(String value, String name, int maxLength) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty() || result.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1.." + maxLength + " characters");
        }
        return result;
    }
}
