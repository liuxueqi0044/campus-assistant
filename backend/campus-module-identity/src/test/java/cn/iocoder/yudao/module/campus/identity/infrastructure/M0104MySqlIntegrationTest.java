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
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt-in M01.04 schema, optimistic-CAS and rollback evidence against a disposable MySQL schema. */
@Tag("M01.04")
@EnabledIfEnvironmentVariable(named = "CAMPUS_M0104_MYSQL_URL", matches = ".+")
class M0104MySqlIntegrationTest {

    private static final String URL = System.getenv("CAMPUS_M0104_MYSQL_URL");
    private static final String USERNAME = System.getenv("CAMPUS_M0104_MYSQL_USERNAME");
    private static final String PASSWORD = System.getenv("CAMPUS_M0104_MYSQL_PASSWORD");

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
                WHERE table_schema = DATABASE()
                  AND table_name IN ('campus_operator_grant', 'campus_operator_grant_audit',
                                     'campus_operator_authorization_version')
                """, Integer.class)).isEqualTo(3);
        jdbc.update("""
                INSERT INTO campus_school (id, code, name, timezone, status, config_version)
                VALUES (1, 'S1', 'School One', 'Asia/Shanghai', 'ENABLED', 1),
                       (2, 'S2', 'School Two', 'Asia/Shanghai', 'ENABLED', 1)
                """);
        jdbc.update("""
                INSERT INTO campus_campus (id, school_id, code, name, timezone, status, config_version)
                VALUES (11, 1, 'C11', 'Campus 11', 'Asia/Shanghai', 'ENABLED', 1),
                       (21, 2, 'C21', 'Campus 21', 'Asia/Shanghai', 'ENABLED', 1)
                """);
    }

    @AfterEach
    void cleanUp() {
        fixtureFlyway().clean();
    }

    @Test
    void scopeShapeAndSchoolCampusRelationshipAreDatabaseInvariants() {
        assertThatThrownBy(() -> insertGrant(901, "PLATFORM", 1L, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGrant(901, "CAMPUS", 1L, 21L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_operator_grant", Integer.class)).isZero();
    }

    @Test
    void oneOperatorHasOnlyOneRowForAnExactScopeEvenAfterRevocation() {
        long grantId = insertGrant(901, "SCHOOL", 1L, null);
        jdbc.update("""
                UPDATE campus_operator_grant
                SET status='REVOKED', revoked_at='2026-09-17 10:00:00.000', revoked_by=900,
                    revocation_reason='fixture revoke', version=2
                WHERE id=?
                """, grantId);

        assertThatThrownBy(() -> insertGrant(901, "SCHOOL", 1L, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_operator_grant", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentUpdatesUsingTheSameVersionProduceExactlyOneWinner() throws Exception {
        long grantId = insertGrant(901, "CAMPUS", 1L, 11L);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(casUpdate(ready, start, grantId,
                    "[\"VERIFICATION_REVIEW\"]"));
            Future<Integer> second = executor.submit(casUpdate(ready, start, grantId,
                    "[\"MEMBERSHIP_REVOKE\"]"));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(0, 1);
            assertThat(jdbc.queryForObject("SELECT version FROM campus_operator_grant WHERE id=?",
                    Long.class, grantId)).isEqualTo(2L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void duplicateAuditFailureRollsBackGrantAndAuthorizationVersionTogether() {
        long grantId = insertGrant(901, "SCHOOL", 1L, null);
        insertAudit(grantId, 1);
        jdbc.update("INSERT INTO campus_operator_authorization_version (operator_id, version) VALUES (901, 1)");
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("UPDATE campus_operator_grant SET version=2 WHERE id=? AND version=1", grantId);
            insertAudit(grantId, 1);
            jdbc.update("UPDATE campus_operator_authorization_version SET version=version+1 WHERE operator_id=901");
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT version FROM campus_operator_grant WHERE id=?",
                Long.class, grantId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT version FROM campus_operator_authorization_version WHERE operator_id=901",
                Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_operator_grant_audit WHERE grant_id=?",
                Integer.class, grantId)).isEqualTo(1);
    }

    private Callable<Integer> casUpdate(CountDownLatch ready, CountDownLatch start,
                                        long grantId, String actionsJson) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for concurrent grant update");
            }
            return new JdbcTemplate(dataSource()).update("""
                    UPDATE campus_operator_grant
                    SET actions_json=?, version=version+1
                    WHERE id=? AND version=1
                    """, actionsJson, grantId);
        };
    }

    private long insertGrant(long operatorId, String scopeType, Long schoolId, Long campusId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO campus_operator_grant
                        (operator_id, scope_type, school_id, campus_id, actions_json, status,
                         valid_from, expires_at, version, granted_by)
                    VALUES (?, ?, ?, ?, JSON_ARRAY('VERIFICATION_REVIEW'), 'ACTIVE',
                            '2026-09-17 09:00:00.000', '2027-09-17 09:00:00.000', 1, 900)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, operatorId);
            statement.setString(2, scopeType);
            if (schoolId == null) {
                statement.setNull(3, java.sql.Types.BIGINT);
            } else {
                statement.setLong(3, schoolId);
            }
            if (campusId == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, campusId);
            }
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        assertThat(generatedId).as("generated operator grant id").isNotNull();
        return generatedId.longValue();
    }

    private void insertAudit(long grantId, long grantVersion) {
        jdbc.update("""
                INSERT INTO campus_operator_grant_audit
                    (grant_id, action, from_status, to_status, operator_id, reason,
                     grant_version, snapshot_json, occurred_at)
                VALUES (?, 'GRANT', 'NONE', 'ACTIVE', 900, 'fixture grant', ?,
                        JSON_OBJECT('grantId', ?), '2026-09-17 09:00:00.000')
                """, grantId, grantVersion, grantId);
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(URL, USERNAME, PASSWORD);
    }

    private static Flyway fixtureFlyway() {
        return Flyway.configure().dataSource(URL, USERNAME, PASSWORD).locations("classpath:db/migration")
                .validateOnMigrate(true).cleanDisabled(false).baselineOnMigrate(false).outOfOrder(false).load();
    }
}
