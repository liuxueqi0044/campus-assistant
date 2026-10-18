package cn.iocoder.yudao.module.campus.identity.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt-in M01.06 lifecycle constraint, CAS and rollback evidence against a disposable MySQL schema. */
@Tag("M01.06")
@EnabledIfEnvironmentVariable(named = "CAMPUS_M0106_MYSQL_URL", matches = ".+")
class M0106MySqlIntegrationTest {

    private static final String URL = System.getenv("CAMPUS_M0106_MYSQL_URL");
    private static final String USERNAME = System.getenv("CAMPUS_M0106_MYSQL_USERNAME");
    private static final String PASSWORD = System.getenv("CAMPUS_M0106_MYSQL_PASSWORD");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        fixtureFlyway().clean();
        fixtureFlyway().migrate();
        dataSource = dataSource();
        jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=DATABASE()
                  AND table_name IN ('campus_account_lifecycle', 'campus_account_lifecycle_log')
                """, Integer.class)).isEqualTo(2);
    }

    @AfterEach
    void cleanUp() {
        fixtureFlyway().clean();
    }

    @Test
    void closureTimestampShapeAndPositiveVersionsAreDatabaseInvariants() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO campus_account_lifecycle
                    (user_id, status, auth_version, version, status_reason,
                     closure_requested_at, session_revocation_state, last_transition_actor_type,
                     last_transition_by)
                VALUES (7, 'ACTIVE', 1, 1, 'invalid fixture',
                        '2026-09-17 12:00:00.000', 'PENDING', 'OPERATOR', 901)
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO campus_account_lifecycle
                    (user_id, status, auth_version, version, status_reason, session_revocation_state,
                     last_transition_actor_type, last_transition_by)
                VALUES (8, 'SUSPENDED', 0, 1, 'invalid fixture', 'PENDING', 'OPERATOR', 901)
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_account_lifecycle", Integer.class)).isZero();
    }

    @Test
    void concurrentLifecycleCommandsUsingSameVersionProduceExactlyOneWinner() throws Exception {
        insertSuspended(7);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> restore = executor.submit(casUpdate(ready, start, "ACTIVE"));
            Future<Integer> limit = executor.submit(casUpdate(ready, start, "LIMITED"));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(restore.get(10, TimeUnit.SECONDS), limit.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(0, 1);
            assertThat(jdbc.queryForObject("SELECT version FROM campus_account_lifecycle WHERE user_id=7",
                    Long.class)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("SELECT auth_version FROM campus_account_lifecycle WHERE user_id=7",
                    Long.class)).isEqualTo(2L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void duplicateAuditFailureRollsBackStatusAndBothVersions() {
        insertSuspended(7);
        insertLog(7, 1, 1);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE campus_account_lifecycle
                    SET status='ACTIVE', version=2, auth_version=2, status_reason='restore fixture'
                    WHERE user_id=7 AND version=1
                    """);
            insertLog(7, 1, 1);
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT status FROM campus_account_lifecycle WHERE user_id=7",
                String.class)).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject("SELECT version FROM campus_account_lifecycle WHERE user_id=7",
                Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM campus_account_lifecycle WHERE user_id=7",
                Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_account_lifecycle_log WHERE user_id=7",
                Integer.class)).isEqualTo(1);
    }

    private Callable<Integer> casUpdate(CountDownLatch ready, CountDownLatch start, String target) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for concurrent lifecycle update");
            }
            return new JdbcTemplate(dataSource()).update("""
                    UPDATE campus_account_lifecycle
                    SET status=?, version=version+1, auth_version=auth_version+1,
                        status_reason='concurrent fixture'
                    WHERE user_id=7 AND version=1
                    """, target);
        };
    }

    private void insertSuspended(long userId) {
        jdbc.update("""
                INSERT INTO campus_account_lifecycle
                    (user_id, status, auth_version, version, status_reason, session_revocation_state,
                     last_transition_actor_type, last_transition_by)
                VALUES (?, 'SUSPENDED', 1, 1, 'fixture suspend', 'PENDING', 'OPERATOR', 901)
                """, userId);
    }

    private void insertLog(long userId, long lifecycleVersion, long authVersion) {
        jdbc.update("""
                INSERT INTO campus_account_lifecycle_log
                    (user_id, from_status, to_status, reason, actor_type, actor_id,
                     lifecycle_version, auth_version, occurred_at)
                VALUES (?, 'IMPLICIT_ACTIVE', 'SUSPENDED', 'fixture suspend', 'OPERATOR', 901,
                        ?, ?, '2026-09-17 12:00:00.000')
                """, userId, lifecycleVersion, authVersion);
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(URL, USERNAME, PASSWORD);
    }

    private static Flyway fixtureFlyway() {
        return Flyway.configure().dataSource(URL, USERNAME, PASSWORD).locations("classpath:db/migration")
                .validateOnMigrate(true).cleanDisabled(false).baselineOnMigrate(false).outOfOrder(false).load();
    }
}
