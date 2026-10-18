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

/** Opt-in M02.02 schema, binding concurrency, CAS and rollback evidence against a disposable MySQL schema. */
@Tag("M02.02")
@EnabledIfEnvironmentVariable(named = "CAMPUS_M0202_MYSQL_URL", matches = ".+")
class M0202MySqlIntegrationTest {

    private static final String URL = System.getenv("CAMPUS_M0202_MYSQL_URL");
    private static final String USERNAME = System.getenv("CAMPUS_M0202_MYSQL_USERNAME");
    private static final String PASSWORD = System.getenv("CAMPUS_M0202_MYSQL_PASSWORD");
    private static final String PURPOSE = "MEMBERSHIP_VERIFICATION";
    private static final String RESOURCE_TYPE = "MEMBERSHIP_VERIFICATION_PROOF";
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        fixtureFlyway().clean();
        fixtureFlyway().migrate();
        dataSource = dataSource();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE m0202_business_resource (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    asset_id BIGINT UNSIGNED NOT NULL,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('campus_media_asset', 'campus_media_binding')
                """, Integer.class)).isEqualTo(2);
    }

    @AfterEach
    void cleanUp() {
        if (jdbc != null) {
            jdbc.execute("DROP TABLE IF EXISTS m0202_business_resource");
        }
        fixtureFlyway().clean();
    }

    @Test
    void v0010MigrationAndDatabaseConstraintsAreEffective() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE script = 'V0010__M02_02_media_binding.sql' AND success = 1
                """, Integer.class)).isEqualTo(1);

        long assetId = insertValidAsset(7, 1, 1);
        insertBinding(assetId, 1, 7, "proof-1");

        assertThatThrownBy(() -> insertBinding(assetId, 1, 7, "proof-2"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertBinding(999999, 1, 7, "proof-fk"))
                .isInstanceOf(DataAccessException.class);
        long secondAssetId = insertValidAsset(7, 1, 2);
        assertThatThrownBy(() -> insertRawBinding(secondAssetId, 1, 7, "bad-hash", "not-a-hash"))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_media_binding", Integer.class)).isEqualTo(1);
    }

    @Test
    void twoConcurrentResourcesForOneAssetHaveExactlyOneDatabaseWinner() throws Exception {
        long assetId = insertValidAsset(7, 1, 1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<InsertOutcome> first = executor.submit(concurrentInsert(ready, start, assetId, "proof-a"));
            Future<InsertOutcome> second = executor.submit(concurrentInsert(ready, start, assetId, "proof-b"));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<InsertOutcome> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(outcomes).extracting(InsertOutcome::success).containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_media_binding WHERE asset_id=?",
                    Integer.class, assetId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void staleGenerationCasUpdateAffectsZeroRows() {
        long assetId = insertValidAsset(7, 2, 5);

        int changed = jdbc.update("""
                UPDATE campus_media_asset
                SET status='VALID', validated_generation=1, version=version+1
                WHERE id=? AND generation=1 AND version=4
                """, assetId);

        assertThat(changed).isZero();
        assertThat(jdbc.queryForObject("SELECT generation FROM campus_media_asset WHERE id=?", Long.class, assetId))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT version FROM campus_media_asset WHERE id=?", Long.class, assetId))
                .isEqualTo(5L);
    }

    @Test
    void businessResourceAndBindingRollBackTogether() {
        long assetId = insertValidAsset(7, 1, 1);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO m0202_business_resource (asset_id) VALUES (?)", assetId);
            insertBinding(assetId, 1, 7, "proof-rollback");
            throw new IllegalStateException("rollback fixture");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM m0202_business_resource", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_media_binding", Integer.class)).isZero();
    }

    private Callable<InsertOutcome> concurrentInsert(CountDownLatch ready, CountDownLatch start,
                                                      long assetId, String resourceId) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for concurrent binding start");
            }
            try {
                insertBinding(new JdbcTemplate(dataSource()), assetId, 1, 7, resourceId);
                return new InsertOutcome(true, "");
            } catch (DataAccessException rejected) {
                return new InsertOutcome(false, rejected.getClass().getSimpleName());
            }
        };
    }

    private long insertValidAsset(long ownerId, long generation, long version) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO campus_media_asset
                        (owner_id, purpose, original_filename, declared_mime, declared_size,
                         object_key, generation, status, idempotency_key_hash, request_hash, ticket_nonce,
                         ticket_expires_at, version, actual_mime, actual_size, content_sha256,
                         validated_generation, validated_at)
                    VALUES (?, ?, 'fixture.jpg', 'image/jpeg', 4, ?, ?, 'VALID', ?, ?, ?,
                            '2027-09-18 00:00:00.000', ?, 'image/jpeg', 4, ?, ?,
                            '2026-09-18 00:00:00.000')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, ownerId);
            statement.setString(2, PURPOSE);
            statement.setString(3, "fixture/object/" + ownerId + "/" + generation + "/" + version);
            statement.setLong(4, generation);
            statement.setString(5, fixtureHash(ownerId, generation, version));
            statement.setString(6, HASH);
            statement.setString(7, "fixture-ticket-" + ownerId + "-" + generation + "-" + version);
            statement.setLong(8, version);
            statement.setString(9, HASH);
            statement.setLong(10, generation);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        assertThat(generatedId).as("generated media asset id").isNotNull();
        return generatedId.longValue();
    }

    private static String fixtureHash(long ownerId, long generation, long version) {
        return String.format("%064x", ownerId * 1_000_000L + generation * 1_000L + version);
    }

    private void insertBinding(long assetId, long generation, long ownerId, String resourceId) {
        insertBinding(jdbc, assetId, generation, ownerId, resourceId);
    }

    private static void insertBinding(JdbcTemplate template, long assetId, long generation,
                                      long ownerId, String resourceId) {
        insertRawBinding(template, assetId, generation, ownerId, resourceId, HASH);
    }

    private void insertRawBinding(long assetId, long generation, long ownerId,
                                  String resourceId, String contextHash) {
        insertRawBinding(jdbc, assetId, generation, ownerId, resourceId, contextHash);
    }

    private static void insertRawBinding(JdbcTemplate template, long assetId, long generation, long ownerId,
                                         String resourceId, String contextHash) {
        template.update("""
                INSERT INTO campus_media_binding
                    (asset_id, asset_generation, owner_id, purpose, resource_type, resource_id,
                     context_hash, valid_from, expires_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, '2026-09-18 00:00:00.000', '2027-09-18 00:00:00.000', 1)
                """, assetId, generation, ownerId, PURPOSE, RESOURCE_TYPE, resourceId, contextHash);
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(URL, USERNAME, PASSWORD);
    }

    private static Flyway fixtureFlyway() {
        return Flyway.configure().dataSource(URL, USERNAME, PASSWORD).locations("classpath:db/migration")
                .validateOnMigrate(true).cleanDisabled(false).baselineOnMigrate(false).outOfOrder(false).load();
    }

    private record InsertOutcome(boolean success, String rejectionType) {
    }
}
