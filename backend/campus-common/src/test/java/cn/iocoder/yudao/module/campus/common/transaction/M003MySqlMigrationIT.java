package cn.iocoder.yudao.module.campus.common.transaction;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real MySQL 8.4 evidence. It is deliberately opt-in so a normal unit build never claims that an
 * in-memory database proved MySQL locking/DDL behavior. Supply CAMPUS_M003_MYSQL_URL, USERNAME and
 * PASSWORD for an isolated disposable schema.
 */
@Tag("M00.03")
@EnabledIfEnvironmentVariable(named = "CAMPUS_M003_MYSQL_URL", matches = ".+")
class M003MySqlMigrationIT {

    private static final String URL = System.getenv("CAMPUS_M003_MYSQL_URL");
    private static final String USERNAME = System.getenv("CAMPUS_M003_MYSQL_USERNAME");
    private static final String PASSWORD = System.getenv("CAMPUS_M003_MYSQL_PASSWORD");

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(URL, USERNAME, PASSWORD);
        jdbc = new JdbcTemplate(dataSource);
        Flyway cleanup = fixtureFlyway();
        cleanup.clean(); // Test configuration alone turns clean on; application configuration always forbids it.
    }

    @AfterEach
    void cleanUp() {
        fixtureFlyway().clean();
    }

    @Test
    void emptyDatabaseShouldMigrateAndSecondRunShouldBeChecksumStable() {
        Flyway flyway = fixtureFlyway();

        // V0001..V0005 plus the M00.03 V900001 acceptance fixture must all apply on an empty schema.
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_m00_03_transaction_example", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                .isEqualTo(6);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void editedAppliedMigrationShouldBeBlockedByChecksumValidation() throws IOException {
        Path directory = Files.createTempDirectory("campus-m003-flyway-");
        try {
            Path migration = directory.resolve("V0001__M00_03_migration_policy_baseline.sql");
            try (InputStream input = getClass().getClassLoader()
                    .getResourceAsStream("db/migration/V0001__M00_03_migration_policy_baseline.sql")) {
                assertThat(input).as("migration must be available in the test classpath").isNotNull();
                Files.copy(input, migration);
            }
            Flyway tempFlyway = Flyway.configure().dataSource(dataSource)
                    .locations("filesystem:" + directory.toAbsolutePath())
                    .cleanDisabled(false).load();
            assertThat(tempFlyway.migrate().migrationsExecuted).isEqualTo(1);
            Files.writeString(migration, "\n-- intentionally modified after apply\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);

            assertThatThrownBy(tempFlyway::validate).isInstanceOf(FlywayValidateException.class);
        } finally {
            try (var paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
        }
    }

    @Test
    void failedSecondWriteShouldRollbackExampleAndOutboxTogether() {
        fixtureFlyway().migrate();
        TransactionExecutor executor = new TransactionExecutor(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> executor.required(TransactionPolicy.withoutRetry("m003-rollback-probe"), status -> {
            jdbc.update("INSERT INTO campus_m00_03_transaction_example (id, business_key, amount_fen, created_at) VALUES (?, ?, ?, ?)",
                    1L, "rollback-probe", 100L, LocalDateTime.now());
            jdbc.update("INSERT INTO campus_m00_03_transaction_example_outbox (id, transaction_example_id, event_key, created_at) VALUES (?, ?, ?, ?)",
                    1L, 1L, "rollback-probe-event", LocalDateTime.now());
            throw new IllegalStateException("fault injected after the second write");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_m00_03_transaction_example", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_m00_03_transaction_example_outbox", Integer.class)).isZero();
    }

    @Test
    void retryShouldRestartWholeTransactionAfterWhitelistedConflict() {
        fixtureFlyway().migrate();
        TransactionExecutor executor = new TransactionExecutor(new DataSourceTransactionManager(dataSource));
        AtomicInteger attempts = new AtomicInteger();

        executor.required(TransactionPolicy.retryMySqlLockConflicts("m003-retry-probe", 2), status -> {
            if (attempts.incrementAndGet() == 1) {
                throw new CannotAcquireLockException("injected lock timeout");
            }
            jdbc.update("INSERT INTO campus_m00_03_transaction_example (id, business_key, amount_fen, created_at) VALUES (?, ?, ?, ?)",
                    2L, "retry-probe", 100L, LocalDateTime.now());
            return null;
        });

        assertThat(attempts).hasValue(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_m00_03_transaction_example", Integer.class)).isEqualTo(1);
    }

    private Flyway fixtureFlyway() {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .locations("classpath:db/migration", "classpath:m003-fixtures")
                .validateOnMigrate(true).cleanDisabled(false).baselineOnMigrate(false).outOfOrder(false).load();
    }
}
