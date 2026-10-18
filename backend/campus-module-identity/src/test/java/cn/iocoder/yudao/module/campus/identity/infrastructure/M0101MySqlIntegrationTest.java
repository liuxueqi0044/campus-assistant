package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.api.IdentityApi;
import cn.iocoder.yudao.module.campus.identity.application.LoginApplicationService;
import cn.iocoder.yudao.module.campus.identity.application.WechatIdentityBindingService;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.WechatIdentityBindingDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Opt-in MySQL 8.4 A-level evidence for M01.01. It drives the production transactional binding
 * service through a Spring proxy. {@code m0101_test_member} is a test-only upstream-member fixture;
 * real member/OAuth2 integration remains B-level evidence.
 */
@Tag("M01.01")
@EnabledIfEnvironmentVariable(named = "CAMPUS_M0101_MYSQL_URL", matches = ".+")
class M0101MySqlIntegrationTest {

    private static final String URL = System.getenv("CAMPUS_M0101_MYSQL_URL");
    private static final String USERNAME = System.getenv("CAMPUS_M0101_MYSQL_USERNAME");
    private static final String PASSWORD = System.getenv("CAMPUS_M0101_MYSQL_PASSWORD");

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private TestDoubles doubles;
    private WechatIdentityBindingService bindingService;
    private LoginApplicationService loginService;

    @BeforeEach
    void setUp() {
        fixtureFlyway().clean();
        fixtureFlyway().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(URL, USERNAME, PASSWORD));
        jdbc.execute("""
                CREATE TABLE m0101_test_member (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    nickname VARCHAR(64) NOT NULL,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(TxConfig.class, () -> new TxConfig(URL, USERNAME, PASSWORD));
        context.refresh();
        doubles = context.getBean(TestDoubles.class);
        bindingService = context.getBean(WechatIdentityBindingService.class);
        loginService = context.getBean(LoginApplicationService.class);
    }

    @AfterEach
    void cleanUp() {
        if (context != null) {
            context.close();
        }
        fixtureFlyway().clean();
    }

    @Test
    void concurrentLegitimateCodesResolveToOneMemberAndBindingWhileBothLoginsSucceed() throws Exception {
        WechatIdentity trusted = new WechatIdentity("miniapp-a", "provider-open-a", null);
        when(doubles.wechatAdapter.exchangeCode("code-one")).thenReturn(trusted);
        when(doubles.wechatAdapter.exchangeCode("code-two")).thenReturn(trusted);
        doubles.armInitialOpenIdLookupBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Outcome>> futures = List.of(executor.submit(invokeLogin("code-one")), executor.submit(invokeLogin("code-two")));
            doubles.awaitInitialLookups();
            doubles.releaseInitialLookups();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }
            assertThat(outcomes).allMatch(Outcome::success);
            assertThat(outcomes).extracting(Outcome::memberId).containsOnly(outcomes.get(0).memberId());
            assertThat(count("m0101_test_member")).isEqualTo(1);
            assertThat(count("campus_wechat_identity_binding")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void bindingInsertFailureRollsBackTheMemberCreatedEarlierInTheSameTransaction() {
        doubles.failNextBindingInsert();

        assertThatThrownBy(() -> bindingService.resolveOrCreateMember(new WechatIdentity("miniapp-a", "provider-open-a", null)))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(count("m0101_test_member")).isZero();
        assertThat(count("campus_wechat_identity_binding")).isZero();
    }

    @Test
    void invalidCodeCreatesNoMemberBindingOrOutboxEvent() {
        CampusApiException unavailable = new CampusApiException(CampusErrorCodes.IDENTITY_PROVIDER_UNAVAILABLE);
        when(doubles.wechatAdapter.exchangeCode("invalid-code")).thenThrow(unavailable);

        assertThatThrownBy(() -> loginService.loginWithWechatCode("invalid-code")).isSameAs(unavailable);
        assertThat(count("m0101_test_member")).isZero();
        assertThat(count("campus_wechat_identity_binding")).isZero();
        assertThat(count("campus_outbox")).isZero();
    }

    @Test
    void forgedClientOpenIdNeverOverridesTheTrustedExchangeResult() {
        // login accepts only a code; the adapter result below is the trusted server-side identity.
        when(doubles.wechatAdapter.exchangeCode("code-with-forged-openid"))
                .thenReturn(new WechatIdentity("miniapp-a", "provider-open-a", null));

        IdentityApi.IdentityLoginResult result = loginService.loginWithWechatCode("code-with-forged-openid");

        assertThat(result.userId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT open_id FROM campus_wechat_identity_binding", String.class))
                .isEqualTo("provider-open-a");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_wechat_identity_binding WHERE open_id = 'forged-openid'", Integer.class))
                .isZero();
    }

    private Callable<Outcome> invokeLogin(String code) {
        return () -> {
            try {
                return Outcome.success(loginService.loginWithWechatCode(code).userId());
            } catch (RuntimeException exception) {
                return Outcome.failure(exception);
            }
        };
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private record Outcome(Long memberId, RuntimeException failure) {
        static Outcome success(Long memberId) { return new Outcome(memberId, null); }
        static Outcome failure(RuntimeException failure) { return new Outcome(null, failure); }
        boolean success() { return failure == null; }
    }

    private static Flyway fixtureFlyway() {
        return Flyway.configure().dataSource(URL, USERNAME, PASSWORD).locations("classpath:db/migration")
                .validateOnMigrate(true).cleanDisabled(false).baselineOnMigrate(false).outOfOrder(false).load();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TxConfig {
        private final String url;
        private final String username;
        private final String password;

        TxConfig(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        @Bean DataSource dataSource() { return new DriverManagerDataSource(url, username, password); }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean TestDoubles testDoubles(JdbcTemplate jdbcTemplate) { return new TestDoubles(jdbcTemplate); }
        @Bean WechatIdentityBindingRepository bindingRepository(TestDoubles doubles) { return doubles.bindingRepository; }
        @Bean MemberAccountAdapter memberAccountAdapter(TestDoubles doubles) { return doubles.memberAccountAdapter; }
        @Bean WechatIdentityAdapter wechatIdentityAdapter(TestDoubles doubles) { return doubles.wechatAdapter; }
        @Bean PlatformTenantExecutor platformTenantExecutor() { return new PlatformTenantExecutor(1L); }
        @Bean WechatIdentityBindingService wechatIdentityBindingService(WechatIdentityBindingRepository repository, MemberAccountAdapter memberAccountAdapter) {
            return new WechatIdentityBindingService(repository, memberAccountAdapter);
        }
        @Bean LoginApplicationService loginApplicationService(WechatIdentityAdapter adapter, WechatIdentityBindingService bindingService,
                                                               MemberAccountAdapter memberAccountAdapter,
                                                               cn.iocoder.yudao.module.campus.identity.application.port.AccountStatusPort accountStatusPort,
                                                               PlatformTenantExecutor executor) {
            return new LoginApplicationService(adapter, bindingService, memberAccountAdapter,
                    accountStatusPort, executor);
        }
        @Bean cn.iocoder.yudao.module.campus.identity.application.port.AccountStatusPort accountStatusPort() {
            return userId -> { };
        }
    }

    static final class TestDoubles {
        private final JdbcTemplate jdbc;
        private final WechatIdentityBindingRepository bindingRepository = mock(WechatIdentityBindingRepository.class);
        private final MemberAccountAdapter memberAccountAdapter = mock(MemberAccountAdapter.class);
        private final WechatIdentityAdapter wechatAdapter = mock(WechatIdentityAdapter.class);
        private final AtomicBoolean failNextBindingInsert = new AtomicBoolean();
        private final AtomicInteger delayedLookups = new AtomicInteger();
        private volatile CountDownLatch lookupReady;
        private volatile CountDownLatch lookupRelease;

        TestDoubles(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
            when(bindingRepository.findByAppIdAndOpenId(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> {
                        WechatIdentityBindingDO binding = findByOpenId(invocation.getArgument(0), invocation.getArgument(1));
                        pauseAfterInitialLookup(binding);
                        return binding;
                    });
            when(bindingRepository.findByAppIdAndUnionId(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                    .thenAnswer(invocation -> findByUnionId(invocation.getArgument(0), invocation.getArgument(1)));
            org.mockito.Mockito.doAnswer(invocation -> {
                if (failNextBindingInsert.compareAndSet(true, false)) {
                    throw new DuplicateKeyException("forced binding insert failure");
                }
                WechatIdentityBindingDO binding = invocation.getArgument(0);
                jdbc.update("INSERT INTO campus_wechat_identity_binding (app_id, open_id, union_id, member_id) VALUES (?, ?, ?, ?)",
                        binding.getAppId(), binding.getOpenId(), binding.getUnionId(), binding.getMemberId());
                return null;
            }).when(bindingRepository).insert(org.mockito.ArgumentMatchers.any());
            when(memberAccountAdapter.createWechatMember()).thenAnswer(invocation -> createMember());
            when(memberAccountAdapter.getRequiredActiveMember(org.mockito.ArgumentMatchers.anyLong()))
                    .thenAnswer(invocation -> member(invocation.getArgument(0)));
            when(memberAccountAdapter.issueMemberToken(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
                OAuth2AccessTokenRespDTO token = new OAuth2AccessTokenRespDTO();
                token.setAccessToken("test-member-token");
                token.setExpiresTime(LocalDateTime.of(2026, 9, 17, 12, 0));
                return token;
            });
        }

        void armInitialOpenIdLookupBarrier(int participants) {
            delayedLookups.set(participants);
            lookupReady = new CountDownLatch(participants);
            lookupRelease = new CountDownLatch(1);
        }

        void awaitInitialLookups() throws InterruptedException { assertThat(lookupReady.await(10, TimeUnit.SECONDS)).isTrue(); }
        void releaseInitialLookups() { lookupRelease.countDown(); }
        void failNextBindingInsert() { failNextBindingInsert.set(true); }

        private void pauseAfterInitialLookup(WechatIdentityBindingDO binding) throws InterruptedException {
            if (binding == null && delayedLookups.getAndUpdate(current -> Math.max(0, current - 1)) > 0) {
                lookupReady.countDown();
                if (!lookupRelease.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent binding start");
                }
            }
        }

        private WechatIdentityBindingDO findByOpenId(String appId, String openId) {
            List<WechatIdentityBindingDO> rows = jdbc.query("SELECT app_id, open_id, union_id, member_id FROM campus_wechat_identity_binding WHERE app_id = ? AND open_id = ?",
                    (rs, rowNum) -> binding(rs.getString("app_id"), rs.getString("open_id"), rs.getString("union_id"), rs.getLong("member_id")), appId, openId);
            return rows.isEmpty() ? null : rows.get(0);
        }

        private WechatIdentityBindingDO findByUnionId(String appId, String unionId) {
            if (unionId == null) return null;
            List<WechatIdentityBindingDO> rows = jdbc.query("SELECT app_id, open_id, union_id, member_id FROM campus_wechat_identity_binding WHERE app_id = ? AND union_id = ?",
                    (rs, rowNum) -> binding(rs.getString("app_id"), rs.getString("open_id"), rs.getString("union_id"), rs.getLong("member_id")), appId, unionId);
            return rows.isEmpty() ? null : rows.get(0);
        }

        private MemberUserDO createMember() {
            jdbc.update("INSERT INTO m0101_test_member (nickname) VALUES ('微信用户')");
            return member(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
        }

        private static MemberUserDO member(Long id) {
            MemberUserDO member = new MemberUserDO();
            member.setId(id);
            member.setName("M01.01 fixture");
            return member;
        }

        private static WechatIdentityBindingDO binding(String appId, String openId, String unionId, long memberId) {
            WechatIdentityBindingDO binding = new WechatIdentityBindingDO();
            binding.setAppId(appId);
            binding.setOpenId(openId);
            binding.setUnionId(unionId);
            binding.setMemberId(memberId);
            return binding;
        }
    }
}
