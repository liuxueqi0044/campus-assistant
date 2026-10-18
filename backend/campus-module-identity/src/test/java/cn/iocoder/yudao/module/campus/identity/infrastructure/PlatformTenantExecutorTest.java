package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformTenantExecutorTest {

    private final PlatformTenantExecutor executor = new PlatformTenantExecutor(1L);

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void forcesPlatformTenantAndRestoresAnIgnoredCallerContext() {
        TenantContextHolder.setTenantId(987L);
        TenantContextHolder.setIgnore(true);

        String result = executor.execute(() -> {
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(1L);
            assertThat(TenantContextHolder.isIgnore()).isFalse();
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(987L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
    }

    @Test
    void preservesCampusExceptionWithoutTenantUtilsCallableWrappingAndStillRestoresContext() {
        TenantContextHolder.setTenantId(321L);
        TenantContextHolder.setIgnore(true);
        CampusApiException expected = new CampusApiException(CampusErrorCodes.ACCOUNT_DISABLED);

        assertThatThrownBy(() -> executor.execute(() -> {
            throw expected;
        })).isSameAs(expected);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(321L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
    }
}
