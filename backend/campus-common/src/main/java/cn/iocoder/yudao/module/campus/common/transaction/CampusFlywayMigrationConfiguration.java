package cn.iocoder.yudao.module.campus.common.transaction;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Converts every Flyway startup failure into the stable M00.03 blocked-migration semantic. */
@Configuration(proxyBeanMethods = false)
public class CampusFlywayMigrationConfiguration {

    @Bean
    public FlywayMigrationStrategy campusFlywayMigrationStrategy() {
        return new CampusFlywayMigrationStrategy();
    }

    private static final class CampusFlywayMigrationStrategy implements FlywayMigrationStrategy {
        @Override
        public void migrate(Flyway flyway) {
            try {
                flyway.migrate();
            } catch (FlywayException exception) {
                throw new MigrationBlockedException(exception);
            }
        }
    }
}
