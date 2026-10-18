package cn.iocoder.yudao.module.campus.common.transaction;

/** A Flyway validation or execution failure that must block startup instead of being ignored. */
public final class MigrationBlockedException extends IllegalStateException {

    public static final String CODE = "MIGRATION_BLOCKED";

    public MigrationBlockedException(Throwable cause) {
        super(CODE + ": inspect schema history and apply a reviewed forward repair; do not edit applied SQL", cause);
    }
}
