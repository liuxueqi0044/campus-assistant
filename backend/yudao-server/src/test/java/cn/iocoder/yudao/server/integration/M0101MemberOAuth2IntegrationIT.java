package cn.iocoder.yudao.server.integration;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.infrastructure.WechatIdentity;
import cn.iocoder.yudao.module.campus.identity.infrastructure.WechatIdentityAdapter;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.member.service.user.MemberUserServiceImpl;
import cn.iocoder.yudao.module.system.api.oauth2.OAuth2TokenApiImpl;
import cn.iocoder.yudao.server.YudaoServerApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * B-level evidence: an HTTP request traverses the production identity, member and OAuth2 beans.
 * The only replacement is {@link WechatIdentityAdapter}, the explicitly allowed external code
 * exchange boundary. The caller must provision a disposable MySQL schema imported from the fixed
 * upstream baseline, explicitly baselined at V0, then migrated with campus migrations; Flyway's
 * automatic baseline remains disabled.
 */
@Tag("M01.01-B")
@EnabledIfEnvironmentVariable(named = "CAMPUS_M0101_B_MYSQL_URL", matches = ".+")
@SpringBootTest(classes = YudaoServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=local", "spring.flyway.baseline-on-migrate=false",
                "spring.flyway.clean-disabled=true", "yudao.security.mock-enable=false",
                "spring.quartz.auto-startup=false", "spring.kafka.listener.auto-startup=false",
                "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.listener.direct.auto-startup=false",
                "spring.boot.admin.client.enabled=false", "campus.outbox.worker-enabled=false", "yudao.websocket.enable=false"})
class M0101MemberOAuth2IntegrationIT {

    private static final String APP_ID = "m0101-b-miniapp";

    @MockitoBean
    private WechatIdentityAdapter wechatIdentityAdapter;

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberUserService memberUserService;
    @Autowired private OAuth2TokenCommonApi oauth2TokenCommonApi;
    @LocalServerPort private int port;

    @BeforeEach
    void assertRealAdjacentModuleBeans() throws Exception {
        assertThat((Object) AopTestUtils.getUltimateTargetObject(memberUserService)).isInstanceOf(MemberUserServiceImpl.class);
        assertThat((Object) AopTestUtils.getUltimateTargetObject(oauth2TokenCommonApi)).isInstanceOf(OAuth2TokenApiImpl.class);
    }

    @Test
    void firstLoginCreatesMemberBindingAndMemberToken() throws Exception {
        String openId = openId();
        when(wechatIdentityAdapter.exchangeCode("first-" + openId)).thenReturn(new WechatIdentity(APP_ID, openId, null));

        Login login = login("first-" + openId);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_user WHERE id = ?", Integer.class, login.memberId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_wechat_identity_binding WHERE app_id = ? AND open_id = ? AND member_id = ?",
                Integer.class, APP_ID, openId, login.memberId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_oauth2_access_token WHERE access_token = ? AND user_id = ? AND user_type = ?",
                Integer.class, login.accessToken(), login.memberId(), UserTypeEnum.MEMBER.getValue())).isEqualTo(1);

        // A member bearer token must never authenticate an /admin-api request.
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(login.accessToken());
        var adminResponse = http.exchange("http://localhost:" + port + "/admin-api/system/auth/get-permission-info",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class);
        assertThat(adminResponse.getStatusCode().value()).isIn(200, 401, 403);
        JsonNode adminBody = objectMapper.readTree(adminResponse.getBody());
        assertThat(adminBody.path("code").asInt()).isIn(401, 403);
        assertThat(adminBody.path("msg").asText(adminBody.path("message").asText())).isNotBlank();
    }

    @Test
    void repeatLoginReusesExistingMemberWithoutOverwritingFields() throws Exception {
        String openId = openId();
        when(wechatIdentityAdapter.exchangeCode("first-" + openId)).thenReturn(new WechatIdentity(APP_ID, openId, null));
        Login first = login("first-" + openId);
        jdbc.update("UPDATE member_user SET name = ?, nickname = ? WHERE id = ?", "Preserved Name", "Preserved Nickname", first.memberId());
        when(wechatIdentityAdapter.exchangeCode("again-" + openId)).thenReturn(new WechatIdentity(APP_ID, openId, null));

        Login again = login("again-" + openId);

        assertThat(again.memberId()).isEqualTo(first.memberId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_user WHERE id = ?", Integer.class, first.memberId())).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT name, nickname FROM member_user WHERE id = ?", first.memberId()))
                .containsEntry("name", "Preserved Name").containsEntry("nickname", "Preserved Nickname");
    }

    @Test
    void disabledMemberCannotLoginAgainOrCreateAnotherToken() throws Exception {
        String openId = openId();
        when(wechatIdentityAdapter.exchangeCode("first-disabled-" + openId))
                .thenReturn(new WechatIdentity(APP_ID, openId, null));
        Login first = login("first-disabled-" + openId);
        int bindingsBefore = jdbc.queryForObject("""
                SELECT COUNT(*) FROM campus_wechat_identity_binding
                 WHERE app_id = ? AND open_id = ? AND member_id = ?
                """, Integer.class, APP_ID, openId, first.memberId());
        int tokensBefore = jdbc.queryForObject("""
                SELECT COUNT(*) FROM system_oauth2_access_token
                 WHERE user_id = ? AND user_type = ?
                """, Integer.class, first.memberId(), UserTypeEnum.MEMBER.getValue());
        jdbc.update("UPDATE member_user SET status = ? WHERE id = ?", CommonStatusEnum.DISABLE.getStatus(),
                first.memberId());
        try {
            when(wechatIdentityAdapter.exchangeCode("again-disabled-" + openId))
                    .thenReturn(new WechatIdentity(APP_ID, openId, null));
            var response = http.postForEntity("http://localhost:" + port + "/app-api/campus/v1/auth/wechat/login",
                    java.util.Map.of("code", "again-disabled-" + openId), String.class);

            assertThat(response.getStatusCode().is4xxClientError()).isTrue();
            JsonNode payload = objectMapper.readTree(response.getBody());
            assertThat(payload.path("code").asInt()).isEqualTo(CampusErrorCodes.ACCOUNT_DISABLED.getCode());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_user WHERE id = ?", Integer.class,
                    first.memberId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM campus_wechat_identity_binding
                     WHERE app_id = ? AND open_id = ? AND member_id = ?
                    """, Integer.class, APP_ID, openId, first.memberId())).isEqualTo(bindingsBefore);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM system_oauth2_access_token
                     WHERE user_id = ? AND user_type = ?
                    """, Integer.class, first.memberId(), UserTypeEnum.MEMBER.getValue())).isEqualTo(tokensBefore);
        } finally {
            jdbc.update("UPDATE member_user SET status = ? WHERE id = ?", CommonStatusEnum.ENABLE.getStatus(),
                    first.memberId());
        }
    }

    @Test
    void failedLoginLeavesNoMemberTokenOrBinding() {
        int members = count("member_user");
        int bindings = count("campus_wechat_identity_binding");
        int tokens = count("system_oauth2_access_token");
        when(wechatIdentityAdapter.exchangeCode("invalid-code")).thenThrow(new CampusApiException(CampusErrorCodes.WECHAT_CODE_INVALID));

        var response = http.postForEntity("http://localhost:" + port + "/app-api/campus/v1/auth/wechat/login",
                java.util.Map.of("code", "invalid-code"), String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).contains(String.valueOf(CampusErrorCodes.WECHAT_CODE_INVALID.getCode()));
        assertThat(count("member_user")).isEqualTo(members);
        assertThat(count("campus_wechat_identity_binding")).isEqualTo(bindings);
        assertThat(count("system_oauth2_access_token")).isEqualTo(tokens);
    }

    private Login login(String code) throws Exception {
        var response = http.postForEntity("http://localhost:" + port + "/app-api/campus/v1/auth/wechat/login",
                java.util.Map.of("code", code), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("accessToken").asText()).isNotBlank();
        return new Login(data.path("userId").asLong(), data.path("accessToken").asText());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static String openId() {
        return "m0101-b-" + UUID.randomUUID().toString().replace("-", "");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.dynamic.datasource.master.url", () -> required("CAMPUS_M0101_B_MYSQL_URL"));
        registry.add("spring.datasource.dynamic.datasource.master.username", () -> required("CAMPUS_M0101_B_MYSQL_USERNAME"));
        registry.add("spring.datasource.dynamic.datasource.master.password", () -> required("CAMPUS_M0101_B_MYSQL_PASSWORD"));
        // Flyway must use its native, non-pooled DataSource here. With Druid, Flyway's best-effort
        // capability probe against performance_schema.user_variables_by_thread invalidates the pooled
        // connection when the deliberately least-privileged CI account cannot read that global table.
        // A native connection safely treats the denied optional probe as unsupported and still validates
        // the already prepared schema without broadening the application's database privileges.
        registry.add("spring.flyway.url", () -> required("CAMPUS_M0101_B_MYSQL_URL"));
        registry.add("spring.flyway.user", () -> required("CAMPUS_M0101_B_MYSQL_USERNAME"));
        registry.add("spring.flyway.password", () -> required("CAMPUS_M0101_B_MYSQL_PASSWORD"));
        // Never allow local profile's lazy slave URL to point at a developer database if a mapper elects to use it.
        registry.add("spring.datasource.dynamic.datasource.slave.url", () -> required("CAMPUS_M0101_B_MYSQL_URL"));
        registry.add("spring.datasource.dynamic.datasource.slave.username", () -> required("CAMPUS_M0101_B_MYSQL_USERNAME"));
        registry.add("spring.datasource.dynamic.datasource.slave.password", () -> required("CAMPUS_M0101_B_MYSQL_PASSWORD"));
        registry.add("spring.data.redis.url", () -> required("CAMPUS_M0101_B_VALKEY_URL"));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("M01.01 B requires " + name);
        }
        return value;
    }

    private record Login(long memberId, String accessToken) {}
}
