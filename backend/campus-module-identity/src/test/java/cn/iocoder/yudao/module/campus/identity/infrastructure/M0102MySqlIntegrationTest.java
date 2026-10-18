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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opt-in real MySQL 8.4 evidence. The environment URL must name a disposable schema: this test
 * explicitly calls Flyway clean and never targets the existing development container.
 */
@Tag("M01.02")
@EnabledIfEnvironmentVariable(named = "CAMPUS_M0102_MYSQL_URL", matches = ".+")
class M0102MySqlIntegrationTest {

    private static final String URL = System.getenv("CAMPUS_M0102_MYSQL_URL");
    private static final String USERNAME = System.getenv("CAMPUS_M0102_MYSQL_USERNAME");
    private static final String PASSWORD = System.getenv("CAMPUS_M0102_MYSQL_PASSWORD");

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(URL, USERNAME, PASSWORD));
        fixtureFlyway().clean();
        assertThat(fixtureFlyway().migrate().migrationsExecuted).isEqualTo(3);
    }

    @AfterEach
    void cleanUp() {
        fixtureFlyway().clean();
    }

    @Test
    void productionMigrationContainsNoMembershipTableAndFixtureIsExplicitlyTestOnly() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_school", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'campus_membership'
                """, Integer.class)).isZero();
    }

    @Test
    void preferenceCompositeForeignKeyRejectsCampusFromAnotherSchool() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO campus_user_browse_preference (user_id, school_id, campus_id)
                VALUES (101, 1, 21)
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_user_browse_preference", Integer.class)).isZero();
    }

    @Test
    void preferenceUniqueKeyAllowsOnlyOneStablePreferencePerUser() {
        jdbc.update("INSERT INTO campus_user_browse_preference (user_id, school_id, campus_id) VALUES (101, 1, 11)");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO campus_user_browse_preference (user_id, school_id, campus_id)
                VALUES (101, 2, 21)
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_user_browse_preference WHERE user_id = 101",
                Integer.class)).isEqualTo(1);
    }

    private static Flyway fixtureFlyway() {
        return Flyway.configure().dataSource(URL, USERNAME, PASSWORD)
                .locations("classpath:db/migration", "classpath:m0102-fixtures")
                .validateOnMigrate(true).cleanDisabled(false).baselineOnMigrate(false).outOfOrder(false).load();
    }
}
