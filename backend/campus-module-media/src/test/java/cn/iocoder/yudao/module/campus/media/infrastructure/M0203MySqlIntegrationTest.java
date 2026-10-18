package cn.iocoder.yudao.module.campus.media.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Statement;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt-in M02.03 schema and grant/audit transaction evidence against a disposable MySQL schema. */
@Tag("M02.03")
@EnabledIfEnvironmentVariable(named = "CAMPUS_M0203_MYSQL_URL", matches = ".+")
class M0203MySqlIntegrationTest {

    private static final String URL = System.getenv("CAMPUS_M0203_MYSQL_URL");
    private static final String USERNAME = System.getenv("CAMPUS_M0203_MYSQL_USERNAME");
    private static final String PASSWORD = System.getenv("CAMPUS_M0203_MYSQL_PASSWORD");
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        fixtureFlyway().clean();
        fixtureFlyway().migrate();
        dataSource = dataSource();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void cleanUp() {
        fixtureFlyway().clean();
    }

    @Test
    void v0011MigrationConstrainsProofsGrantsAndAuditFacts() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE script = 'V0011__M02_03_media_access_log.sql' AND success = 1
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('campus_media_delivery_grant', 'campus_media_access_log')
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'campus_media_access_log'
                """, String.class)).noneMatch(name -> name.matches("(?i).*(token|object_key|url|content).*$"));

        insertScope();
        insertPendingVerification(101, "proof-stable");
        assertThatThrownBy(() -> insertPendingVerification(102, "proof-stable"))
                .isInstanceOf(DataAccessException.class);

        Target target = insertTarget(101);
        insertGrant(target, "MEMBER", 101, null, null, HASH);
        assertThatThrownBy(() -> insertGrant(target, "MEMBER", 101, "case-1", "review", fixtureHash(2)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertGrant(target, "OPERATOR", 201, null, null, fixtureHash(3)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertGrant(target, "MEMBER", 101, null, null, HASH))
                .isInstanceOf(DataAccessException.class);

        insertAudit(null, "MEMBER", 101, null, null, "DENY");
        assertThatThrownBy(() -> insertAudit(null, "OPERATOR", 201, null, null, "DENY"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void grantAndAllowAuditRollBackAsOneUnit() {
        Target target = insertTarget(101);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            insertAudit(target, "MEMBER", 101, null, null, "ALLOW");
            insertGrant(target, "MEMBER", 101, null, null, HASH);
            throw new IllegalStateException("rollback fixture");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_media_delivery_grant", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_media_access_log", Integer.class)).isZero();
    }

    @Test
    void storedGrantExpiryAndSnapshotRemainStableOnReplayReads() {
        Target target = insertTarget(101);
        insertGrant(target, "MEMBER", 101, null, null, HASH);
        LocalDateTime expiry = jdbc.queryForObject(
                "SELECT expires_at FROM campus_media_delivery_grant WHERE token_hash=?", LocalDateTime.class, HASH);
        long generation = jdbc.queryForObject(
                "SELECT asset_generation FROM campus_media_delivery_grant WHERE token_hash=?", Long.class, HASH);
        long version = jdbc.queryForObject(
                "SELECT asset_version FROM campus_media_delivery_grant WHERE token_hash=?", Long.class, HASH);

        jdbc.queryForMap("SELECT * FROM campus_media_delivery_grant WHERE token_hash=?", HASH);

        assertThat(jdbc.queryForObject(
                "SELECT expires_at FROM campus_media_delivery_grant WHERE token_hash=?", LocalDateTime.class, HASH))
                .isEqualTo(expiry);
        assertThat(generation).isEqualTo(1L);
        assertThat(version).isEqualTo(1L);
    }

    private void insertScope() {
        jdbc.update("INSERT INTO campus_school (id, code, name, timezone, status, config_version) "
                + "VALUES (1, 'school-1', 'School 1', 'Asia/Shanghai', 'ENABLED', 1)");
        jdbc.update("INSERT INTO campus_campus (id, school_id, code, name, timezone, status, config_version) "
                + "VALUES (1, 1, 'campus-1', 'Campus 1', 'Asia/Shanghai', 'ENABLED', 1)");
    }

    private void insertPendingVerification(long userId, String proofReference) {
        jdbc.update("""
                INSERT INTO campus_verification
                    (user_id, school_id, campus_id, method, evidence_refs_json, proof_reference,
                     status, version, submitted_at)
                VALUES (?, 1, 1, 'DOCUMENT', JSON_ARRAY('asset:1'), ?, 'PENDING', 1,
                        CURRENT_TIMESTAMP(3))
                """, userId, proofReference);
    }

    private Target insertTarget(long ownerId) {
        var assetKeys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO campus_media_asset
                        (owner_id, purpose, original_filename, declared_mime, declared_size,
                         object_key, generation, status, idempotency_key_hash, request_hash, ticket_nonce,
                         ticket_expires_at, version, actual_mime, actual_size, content_sha256,
                         validated_generation, validated_at)
                    VALUES (?, 'MEMBERSHIP_VERIFICATION', 'proof.png', 'image/png', 4,
                            ?, 1, 'VALID', ?, ?, ?, DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY), 1,
                            'image/png', 4, ?, 1, CURRENT_TIMESTAMP(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, ownerId);
            statement.setString(2, "fixture/proof/" + ownerId);
            statement.setString(3, fixtureHash(ownerId));
            statement.setString(4, fixtureHash(ownerId + 1));
            statement.setString(5, "fixture-ticket-" + ownerId);
            statement.setString(6, HASH);
            return statement;
        }, assetKeys);
        long assetId = assetKeys.getKey().longValue();

        var bindingKeys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO campus_media_binding
                        (asset_id, asset_generation, owner_id, purpose, resource_type, resource_id,
                         context_hash, valid_from, expires_at, version)
                    VALUES (?, 1, ?, 'MEMBERSHIP_VERIFICATION', 'MEMBERSHIP_VERIFICATION_PROOF',
                            'proof-1', ?, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 MINUTE),
                            DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY), 1)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, assetId);
            statement.setLong(2, ownerId);
            statement.setString(3, HASH);
            return statement;
        }, bindingKeys);
        return new Target(assetId, bindingKeys.getKey().longValue());
    }

    private void insertGrant(Target target, String actorType, long actorId, String caseReference,
                             String reason, String tokenHash) {
        jdbc.update("""
                INSERT INTO campus_media_delivery_grant
                    (token_hash, actor_type, actor_id, asset_id, asset_generation, asset_version,
                     binding_id, case_reference, reason, expires_at, version)
                VALUES (?, ?, ?, ?, 1, 1, ?, ?, ?, DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 2 MINUTE), 1)
                """, tokenHash, actorType, actorId, target.assetId(), target.bindingId(), caseReference, reason);
    }

    private void insertAudit(Target target, String actorType, long actorId, String caseReference,
                             String reason, String decision) {
        jdbc.update("""
                INSERT INTO campus_media_access_log
                    (actor_type, actor_id, asset_id, asset_generation, asset_version, binding_id,
                     resource_type, resource_id, action, purpose, decision, case_reference, reason,
                     audit_labels_json, trace_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN_AUTHORIZED', ?, ?, ?, ?,
                        JSON_ARRAY('fixture'), 'trace-fixture',
                        CURRENT_TIMESTAMP(3))
                """, actorType, actorId,
                target == null ? null : target.assetId(), target == null ? null : 1L, target == null ? null : 1L,
                target == null ? null : target.bindingId(),
                target == null ? null : "MEMBERSHIP_VERIFICATION_PROOF",
                target == null ? null : "proof-1", target == null ? null : "MEMBERSHIP_VERIFICATION",
                decision, caseReference, reason);
    }

    private static String fixtureHash(long value) {
        return String.format("%064x", value);
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(URL, USERNAME, PASSWORD);
    }

    private static Flyway fixtureFlyway() {
        return Flyway.configure().dataSource(URL, USERNAME, PASSWORD).locations("classpath:db/migration")
                .validateOnMigrate(true).cleanDisabled(false).baselineOnMigrate(false).outOfOrder(false).load();
    }

    private record Target(long assetId, long bindingId) {
    }
}
