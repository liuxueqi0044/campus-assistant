package cn.iocoder.yudao.module.campus.identity.application.port;

import java.util.Objects;

/** Aggregated blocker only; no order, fund or case entity is exposed to identity. */
public record AccountObligation(String category, long count, String nextAction) {

    public AccountObligation {
        category = require(category, "category", 64);
        nextAction = require(nextAction, "nextAction", 128);
        if (count <= 0) {
            throw new IllegalArgumentException("obligation count must be positive");
        }
    }

    private static String require(String value, String name, int maxLength) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty() || result.length() > maxLength) {
            throw new IllegalArgumentException(name + " has invalid length");
        }
        return result;
    }
}
