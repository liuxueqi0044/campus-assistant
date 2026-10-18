package cn.iocoder.yudao.module.campus.identity.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

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

/** Opt-in M01.03 schema and optimistic-review evidence against a disposable MySQL schema. */
@Tag("M01.03")
@EnabledIfEnvironmentVariable(named = "CAMPUS_M0103_MYSQL_URL", matches = ".+")
class M0103MySqlIntegrationTest {

    private static final String URL = System.getenv("CAMPUS_M0103_MYSQL_URL");
    private static final String USERNAME = System.getenv("CAMPUS_M0103_MYSQL_USERNAME");
    private static final String PASSWORD = System.getenv("CAMPUS_M0103_MYSQL_PASSWORD");

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        fixtureFlyway().clean();
        fixtureFlyway().migrate();
        jdbc = jdbc();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('campus_verification', 'campus_verification_review', 'campus_membership')
                """, Integer.class)).isEqualTo(3);
        jdbc.update("""
                INSERT INTO campus_school (id, code, name, timezone, status, config_version)
                VALUES (1, 'S1', 'School One', 'Asia/Shanghai', 'ENABLED', 1),
                       (2, 'S2', 'School Two', 'Asia/Shanghai', 'ENABLED', 1)
                """);
        jdbc.update("""
                INSERT INTO campus_campus (id, school_id, code, name, timezone, status, config_version)
                VALUES (11, 1, 'C11', 'Campus 11', 'Asia/Shanghai', 'ENABLED', 1),
                       (12, 1, 'C12', 'Campus 12', 'Asia/Shanghai', 'ENABLED', 1),
                       (21, 2, 'C21', 'Campus 21', 'Asia/Shanghai', 'ENABLED', 1)
                """);
    }

    @AfterEach
    void cleanUp() {
        fixtureFlyway().clean();
    }

    @Test
    void oneUserHasOnlyOneMembershipRowPerSchool() {
        long firstVerification = insertPendingVerification(101, 1, 11);
        approveVerification(firstVerification);
        insertMembership(101, 1, 11, firstVerification);
        long secondVerification = insertPendingVerification(101, 1, 12);
        approveVerification(secondVerification);

        assertThatThrownBy(() -> insertMembership(101, 1, 12, secondVerification))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_membership WHERE user_id=101 AND school_id=1",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void compositeForeignKeysRejectCampusFromAnotherSchool() {
        assertThatThrownBy(() -> insertPendingVerification(101, 1, 21))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_verification", Integer.class)).isZero();
    }

    @Test
    void onlyOnePendingAttemptIsAllowedPerUserAndSchoolButResubmissionAfterDecisionIsAllowed() {
        long first = insertPendingVerification(101, 1, 11);
        assertThatThrownBy(() -> insertPendingVerification(101, 1, 12))
                .isInstanceOf(DataIntegrityViolationException.class);

        rejectVerification(first);
        long second = insertPendingVerification(101, 1, 12);

        assertThat(second).isPositive().isNotEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_verification WHERE user_id=101 AND school_id=1",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void opposingReviewersUsingTheSameVersionProduceExactlyOneWinner() throws Exception {
        long verificationId = insertPendingVerification(101, 1, 11);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> approval = executor.submit(reviewUpdate(ready, start, verificationId, "APPROVED", 901));
            Future<Integer> rejection = executor.submit(reviewUpdate(ready, start, verificationId, "REJECTED", 902));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> affected = List.of(approval.get(10, TimeUnit.SECONDS), rejection.get(10, TimeUnit.SECONDS));
            assertThat(affected).containsExactlyInAnyOrder(0, 1);
            assertThat(jdbc.queryForObject("SELECT version FROM campus_verification WHERE id=?", Long.class,
                    verificationId)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("SELECT status FROM campus_verification WHERE id=?", String.class,
                    verificationId)).isIn("APPROVED", "REJECTED");
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Integer> reviewUpdate(CountDownLatch ready, CountDownLatch start, long verificationId,
                                           String targetStatus, long operatorId) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for concurrent review start");
            }
            JdbcTemplate concurrentJdbc = jdbc();
            boolean approval = "APPROVED".equals(targetStatus);
            return concurrentJdbc.update("""
                    UPDATE campus_verification
                    SET status=?, version=version+1, reviewed_at='2026-09-17 10:00:00.000', reviewed_by=?,
                        review_reason='fixture review', valid_from=?, expires_at=?
                    WHERE id=? AND status='PENDING' AND version=1
                    """, targetStatus, operatorId,
                    approval ? "2026-09-17 10:00:00.000" : null,
                    approval ? "2027-09-17 10:00:00.000" : null,
                    verificationId);
        };
    }

    private long insertPendingVerification(long userId, long schoolId, long campusId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO campus_verification
                        (user_id, school_id, campus_id, method, evidence_refs_json, proof_reference,
                         status, version, submitted_at)
                    VALUES (?, ?, ?, 'STUDENT_CARD', JSON_ARRAY('asset-fixture'), 'proof-fixture',
                            'PENDING', 1, '2026-09-17 09:00:00.000')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setLong(2, schoolId);
            statement.setLong(3, campusId);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        assertThat(generatedId).as("generated verification id").isNotNull();
        return generatedId.longValue();
    }

    private void approveVerification(long verificationId) {
        jdbc.update("""
                UPDATE campus_verification
                SET status='APPROVED', version=version+1, reviewed_at='2026-09-17 10:00:00.000', reviewed_by=901,
                    review_reason='approved', valid_from='2026-09-17 10:00:00.000',
                    expires_at='2027-09-17 10:00:00.000'
                WHERE id=? AND status='PENDING' AND version=1
                """, verificationId);
    }

    private void rejectVerification(long verificationId) {
        jdbc.update("""
                UPDATE campus_verification
                SET status='REJECTED', version=version+1, reviewed_at='2026-09-17 10:00:00.000', reviewed_by=902,
                    review_reason='rejected'
                WHERE id=? AND status='PENDING' AND version=1
                """, verificationId);
    }

    private void insertMembership(long userId, long schoolId, long campusId, long verificationId) {
        jdbc.update("""
                INSERT INTO campus_membership
                    (user_id, school_id, campus_id, source_verification_id, status,
                     effective_from, expires_at, scope_version)
                VALUES (?, ?, ?, ?, 'ACTIVE', '2026-09-17 10:00:00.000', '2027-09-17 10:00:00.000', 1)
                """, userId, schoolId, campusId, verificationId);
    }

    private static JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(URL, USERNAME, PASSWORD));
    }

    private static Flyway fixtureFlyway() {
        return Flyway.configure().dataSource(URL, USERNAME, PASSWORD).locations("classpath:db/migration")
                .validateOnMigrate(true).cleanDisabled(false).baselineOnMigrate(false).outOfOrder(false).load();
    }
}
