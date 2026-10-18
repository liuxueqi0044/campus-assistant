package cn.iocoder.yudao.server.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class M0101BDatabasePreparationGuardTest {

    @Test
    void acceptsExplicitLoopbackPortAndDisposableSchema() {
        String schema = M0101BDatabasePreparationIT.validateDisposableMySqlUrl(
                "jdbc:mysql://127.0.0.1:3306/campus_acceptance?useSSL=false&serverTimezone=UTC");

        assertThat(schema).isEqualTo("campus_acceptance");
    }

    @Test
    void rejectsRemoteHostEvenWhenItUsesTheDefaultPort() {
        assertThatThrownBy(() -> M0101BDatabasePreparationIT.validateDisposableMySqlUrl(
                "jdbc:mysql://database.example:3306/campus_acceptance?useSSL=false"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    void rejectsNonDisposableSchemaAndUnapprovedParameters() {
        assertThatThrownBy(() -> M0101BDatabasePreparationIT.validateDisposableMySqlUrl(
                "jdbc:mysql://localhost:3306/campus?useSSL=false"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("_it or _acceptance");
        assertThatThrownBy(() -> M0101BDatabasePreparationIT.validateDisposableMySqlUrl(
                "jdbc:mysql://localhost:3306/campus_it?createDatabaseIfNotExist=true"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe parameter");
    }
}
