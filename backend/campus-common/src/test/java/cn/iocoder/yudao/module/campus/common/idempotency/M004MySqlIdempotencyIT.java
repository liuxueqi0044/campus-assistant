package cn.iocoder.yudao.module.campus.common.idempotency;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opt-in MySQL 8 evidence. Set CAMPUS_MYSQL_IT_URL/USERNAME/PASSWORD (the common CI names) or
 * the module-specific CAMPUS_M004_MYSQL_* aliases. Without them this class is skipped and no
 * in-memory database is presented as proof of MySQL locking behavior.
 */
@Tag("M00.04")
@EnabledIf("mysqlConfigured")
class M004MySqlIdempotencyIT {

    /**
     * Acceptance-only mutation. It is intentionally a JVM property rather than an application
     * setting, and is applied only after Flyway created this isolated test schema. The CI guard
     * runs the concurrent case with this flag and requires the run to fail; production code never
     * reads or writes this switch.
     */
    private static final boolean DROP_IDENTITY_UNIQUE =
            Boolean.getBoolean("campus.m004.mutation.drop-unique");

    private static final String URL = env("CAMPUS_MYSQL_IT_URL", "CAMPUS_M004_MYSQL_URL", "CAMPUS_M003_MYSQL_URL");
    private static final String USERNAME = env("CAMPUS_MYSQL_IT_USERNAME", "CAMPUS_M004_MYSQL_USERNAME",
            "CAMPUS_M003_MYSQL_USERNAME");
    private static final String PASSWORD = env("CAMPUS_MYSQL_IT_PASSWORD", "CAMPUS_M004_MYSQL_PASSWORD",
            "CAMPUS_M003_MYSQL_PASSWORD");

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private IdempotentCommandExecutor executor;

    static boolean mysqlConfigured() {
        return URL != null && !URL.isBlank() && USERNAME != null && PASSWORD != null;
    }

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(URL, USERNAME, PASSWORD);
        jdbc = new JdbcTemplate(dataSource);
        fixtureFlyway().clean();
        fixtureFlyway().migrate();
        if (DROP_IDENTITY_UNIQUE) {
            jdbc.execute("ALTER TABLE campus_idempotency DROP INDEX uk_campus_idempotency_identity");
        }
        jdbc.execute("""
                CREATE TABLE campus_m00_04_test_business (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    business_key VARCHAR(128) NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_campus_m004_test_business_key (business_key)
                ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
                """);
        executor = new IdempotentCommandExecutor(new DataSourceTransactionManager(dataSource),
                new JdbcIdempotencyRepository(jdbc), new RequestCanonicalizer(new ObjectMapper()),
                new ObjectMapper());
    }

    @AfterEach
    void cleanUp() {
        fixtureFlyway().clean();
    }

    @Test
    void fiftyConcurrentSameKeyCallsWriteOneBusinessRow() throws Exception {
        int callers = 50;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger handlerCalls = new AtomicInteger();
        List<Future<IdempotentCommandResult<String>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    IdempotentCommand command = command("same-key", Map.of("amount", 10));
                    return executor.execute(command, String.class, () -> {
                        handlerCalls.incrementAndGet();
                        jdbc.update("INSERT INTO campus_m00_04_test_business (business_key) VALUES (?)", "ORDER-1");
                        return new CommandExecution<>("accepted", "ORDER-1", 1);
                    }, ReplayAuthorizer.allowAll());
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<IdempotentCommandResult<String>> results = new ArrayList<>();
            for (Future<IdempotentCommandResult<String>> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).allSatisfy(result -> {
                assertThat(result.result()).isEqualTo("accepted");
                assertThat(result.businessReference()).isEqualTo("ORDER-1");
            });
            assertThat(handlerCalls).hasValue(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_m00_04_test_business", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_idempotency", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sameKeyDifferentBodyIsConflictWithoutNewBusinessRow() {
        executor.execute(command("conflict-key", Map.of("amount", 10)), String.class, () -> {
            jdbc.update("INSERT INTO campus_m00_04_test_business (business_key) VALUES (?)", "ORDER-2");
            return new CommandExecution<>("accepted", "ORDER-2", 1);
        }, ReplayAuthorizer.allowAll());

        assertThatThrownBy(() -> executor.execute(command("conflict-key", Map.of("amount", 11)), String.class,
                () -> new CommandExecution<>("wrong", "ORDER-3", 1), ReplayAuthorizer.allowAll()))
                .isInstanceOf(CampusApiException.class)
                .extracting(error -> ((CampusApiException) error).getSpec().getName())
                .isEqualTo("CAMPUS_IDEMPOTENCY_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_m00_04_test_business", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void handlerFailureRollsBackReservationAndBusinessWriteThenRetrySucceeds() {
        IdempotentCommand command = command("retry-key", Map.of("amount", 12));
        assertThatThrownBy(() -> executor.execute(command, String.class, () -> {
            jdbc.update("INSERT INTO campus_m00_04_test_business (business_key) VALUES (?)", "ORDER-4");
            throw new IllegalStateException("fault injected before commit");
        }, ReplayAuthorizer.allowAll())).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_idempotency", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_m00_04_test_business", Integer.class)).isZero();

        IdempotentCommandResult<String> retry = executor.execute(command, String.class, () -> {
            jdbc.update("INSERT INTO campus_m00_04_test_business (business_key) VALUES (?)", "ORDER-4");
            return new CommandExecution<>("accepted", "ORDER-4", 1);
        }, ReplayAuthorizer.allowAll());
        assertThat(retry.replayed()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_idempotency", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_m00_04_test_business", Integer.class)).isEqualTo(1);
    }

    private static IdempotentCommand command(String key, Object request) {
        return new IdempotentCommand(1, 7, "m00.04.test.create", key, request, Duration.ofDays(1));
    }

    private Flyway fixtureFlyway() {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .validateOnMigrate(true).cleanDisabled(false).baselineOnMigrate(false).outOfOrder(false).load();
    }

    private static String env(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
