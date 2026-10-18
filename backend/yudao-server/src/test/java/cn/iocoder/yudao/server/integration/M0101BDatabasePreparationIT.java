package cn.iocoder.yudao.server.integration;

import org.apache.ibatis.jdbc.ScriptRunner;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicitly prepares only a disposable B-level schema before the full server starts.
 * The import is intentionally guarded because the fixed upstream SQL drops tables.
 */
@Tag("M01.01-B")
@EnabledIfEnvironmentVariable(named = "CAMPUS_M0101_B_MYSQL_URL", matches = ".+")
class M0101BDatabasePreparationIT {

    private static final Pattern MYSQL_URL = Pattern.compile("^jdbc:mysql://([^:/?;@,]+)(?::\\d+)?/([A-Za-z0-9_]+)(?:\\?(.+))?$");
    private static final Pattern SAFE_PARAMETER_VALUE = Pattern.compile("^[A-Za-z0-9_./:+-]+$");
    private static final Set<String> SAFE_PARAMETER_NAMES = Set.of("useSSL", "serverTimezone", "allowPublicKeyRetrieval",
            "nullCatalogMeansCurrent", "rewriteBatchedStatements", "useUnicode", "characterEncoding", "connectionTimeZone");
    private static final Pattern SCHEMA_NAME = Pattern.compile("^[A-Za-z0-9_]+(?:_it|_acceptance)$");
    private static final Pattern UNSAFE_SQL_DIRECTIVE = Pattern.compile("(?im)^\\s*(?:(?:CREATE|DROP)\\s+DATABASE\\b|USE\\b|DELIMITER\\b)");

    @Test
    void importsFixedUpstreamBaselineThenExplicitlyBaselinesV0AndMigratesCampusSchema() throws Exception {
        String url = required("CAMPUS_M0101_B_MYSQL_URL");
        String schema = validateDisposableMySqlUrl(url);
        assertThat(required("CAMPUS_M0101_B_SCHEMA_GUARD")).isEqualTo(schema);
        assertThat(required("CAMPUS_M0101_B_PREPARE")).isEqualTo("IMPORT_FIXED_BASELINE");
        Path backend = locateBackend();
        try (Connection connection = DriverManager.getConnection(url,
                required("CAMPUS_M0101_B_MYSQL_USERNAME"), required("CAMPUS_M0101_B_MYSQL_PASSWORD"))) {
            runSql(connection, backend.resolve("sql/mysql/ruoyi-vue-pro.sql"));
            runSql(connection, backend.resolve("sql/mysql/quartz.sql"));
            // The upstream base dump intentionally excludes optional business modules. Import the
            // smallest real member-module table needed by this full-server boundary test instead
            // of granting the test a mocked MemberUserService.
            runSql(connection, backend.resolve("yudao-server/src/test/resources/m0101-b/member_user.mysql.sql"));
            assertMemberBaseline(connection);
            Flyway flyway = Flyway.configure().dataSource(connection.getMetaData().getURL(),
                            required("CAMPUS_M0101_B_MYSQL_USERNAME"), required("CAMPUS_M0101_B_MYSQL_PASSWORD"))
                    .locations("classpath:db/migration").baselineOnMigrate(false).cleanDisabled(true)
                    .validateOnMigrate(true).baselineVersion("0").load();
            flyway.baseline();
            flyway.migrate();
        }
    }

    private static void assertMemberBaseline(Connection connection) throws Exception {
        List<String> requiredColumns = List.of("id", "nickname", "status", "password", "register_ip",
                "register_terminal", "deleted", "tenant_id");
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("SHOW COLUMNS FROM member_user")) {
            var actual = new java.util.HashSet<String>();
            while (columns.next()) {
                actual.add(columns.getString("Field"));
            }
            assertThat(actual).containsAll(requiredColumns);
        }
    }

    private static void runSql(Connection connection, Path script) throws Exception {
        assertThat(Files.isRegularFile(script)).as("fixed baseline script %s", script).isTrue();
        String source = Files.readString(script, StandardCharsets.UTF_8);
        assertThat(UNSAFE_SQL_DIRECTIVE.matcher(source).find()).as("baseline script must not switch or create databases: %s", script).isFalse();
        ScriptRunner runner = new ScriptRunner(connection);
        runner.setStopOnError(true);
        runner.setLogWriter(null);
        runner.setErrorLogWriter(null);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(script), StandardCharsets.UTF_8))) {
            runner.runScript(reader);
        }
    }

    private static Path locateBackend() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("sql/mysql/ruoyi-vue-pro.sql");
            if (Files.isRegularFile(candidate) && Files.isRegularFile(cursor.resolve("sql/mysql/quartz.sql"))) {
                return cursor;
            }
        }
        throw new IllegalStateException("cannot locate backend/sql/mysql fixed baseline from " + Path.of("").toAbsolutePath());
    }

    static String validateDisposableMySqlUrl(String url) {
        var match = MYSQL_URL.matcher(url);
        if (!match.matches()) {
            throw new IllegalArgumentException("M01.01 B requires a single-host jdbc:mysql URL with an explicit schema");
        }
        String host = match.group(1).toLowerCase(Locale.ROOT);
        if (!Set.of("127.0.0.1", "localhost").contains(host)) {
            throw new IllegalArgumentException("M01.01 B destructive preparation is restricted to a loopback MySQL host");
        }
        String schema = match.group(2);
        if (!SCHEMA_NAME.matcher(schema).matches()) {
            throw new IllegalArgumentException("M01.01 B schema must end in _it or _acceptance");
        }
        String query = match.group(3);
        if (query != null) {
            for (String parameter : query.split("&")) {
                String[] pair = parameter.split("=", -1);
                if (pair.length != 2 || !SAFE_PARAMETER_NAMES.contains(pair[0]) || !SAFE_PARAMETER_VALUE.matcher(pair[1]).matches()) {
                    throw new IllegalArgumentException("M01.01 B JDBC URL has an unsafe parameter");
                }
            }
        }
        return schema;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("M01.01 B requires " + name);
        }
        return value;
    }
}
