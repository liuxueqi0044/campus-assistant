package cn.iocoder.yudao.module.campus.common.transaction;

import java.util.Objects;

/**
 * A reviewed, forward-only database migration plan.
 *
 * <p>The plan is deliberately data-only: it documents the release boundary and required backup,
 * while Flyway remains the sole schema executor. A plan never grants permission to call
 * {@code clean}, edit an applied migration, or infer a baseline for an existing database.</p>
 */
public record MigrationPlan(String fromVersion, String toVersion, String backupPrerequisite) {

    public MigrationPlan {
        fromVersion = requireText(fromVersion, "fromVersion");
        toVersion = requireText(toVersion, "toVersion");
        backupPrerequisite = requireText(backupPrerequisite, "backupPrerequisite");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
