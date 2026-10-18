package cn.iocoder.yudao.module.campus.identity.application.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountLifecycleConfigurationTest {

    @Test
    void absentObligationIntegrationFailsClosed() {
        AccountLifecycleConfiguration configuration = new AccountLifecycleConfiguration();

        assertThatThrownBy(() -> configuration.unavailableAccountObligationPort().findBlockers(7))
                .isInstanceOfSatisfying(AccountLifecycleException.class,
                        error -> assertThat(error.failure()).isEqualTo(
                                AccountLifecycleFailure.OBLIGATION_UNAVAILABLE));
    }
}
