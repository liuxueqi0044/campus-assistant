package cn.iocoder.yudao.server.integration;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.yudao.framework.common.enums.TerminalEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.infrastructure.WechatIdentityAdapter;
import cn.iocoder.yudao.module.campus.identity.infrastructure.MembershipVerificationRepository;
import cn.iocoder.yudao.module.campus.media.api.MediaStoragePort;
import cn.iocoder.yudao.module.campus.media.infrastructure.LocalPrivateStorageAdapter;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.api.oauth2.OAuth2TokenApiImpl;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.server.YudaoServerApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * B-level HTTP evidence for the tenant and audience boundary around private verification proof
 * downloads. This starts the production FilterChain and uses real MySQL, Valkey, OAuth2, RBAC,
 * campus authorization and local private storage. The only replacement is the unused external
 * WeChat code-exchange adapter, matching {@link M0101MemberOAuth2IntegrationIT}.
 *
 * <p>Run only with a disposable B environment that provides all of
 * {@code CAMPUS_M0101_B_MYSQL_URL}, {@code CAMPUS_M0101_B_MYSQL_USERNAME},
 * {@code CAMPUS_M0101_B_MYSQL_PASSWORD}, and {@code CAMPUS_M0101_B_VALKEY_URL}. The existing
 * M01.01 gate is deliberately reused so one prepared schema/Valkey pair supplies all full-server
 * campus HTTP tests.
 */
@Tag("M02.03-B")
@EnabledIfEnvironmentVariable(named = "CAMPUS_M0101_B_MYSQL_URL", matches = ".+")
@SpringBootTest(classes = YudaoServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=local", "spring.flyway.baseline-on-migrate=false",
                "spring.flyway.clean-disabled=true", "yudao.security.mock-enable=false",
                "spring.quartz.auto-startup=false", "spring.kafka.listener.auto-startup=false",
                "spring.rabbitmq.listener.simple.auto-startup=false",
                "spring.rabbitmq.listener.direct.auto-startup=false", "spring.boot.admin.client.enabled=false",
                "campus.outbox.worker-enabled=false", "yudao.websocket.enable=false",
                "yudao.captcha.enable=false"})
class M0203TenantBoundaryIntegrationIT {

    private static final long PLATFORM_TENANT_ID = 1L;
    private static final long OTHER_TENANT_ID = 2L;
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() * 10L);

    @MockitoBean
    private WechatIdentityAdapter wechatIdentityAdapter;

    @MockitoSpyBean
    private MembershipVerificationRepository verificationRepository;

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OAuth2TokenCommonApi oauth2TokenApi;
    @Autowired private MemberUserService memberUserService;
    @Autowired private PermissionService permissionService;
    @Autowired private MediaStoragePort storage;
    @Autowired private PasswordEncoder passwordEncoder;
    @LocalServerPort private int port;

    private final List<String> storedKeys = new ArrayList<>();

    @BeforeEach
    void assertRealBoundaryBeans() throws Exception {
        assertThat((Object) AopTestUtils.getUltimateTargetObject(oauth2TokenApi))
                .isInstanceOf(OAuth2TokenApiImpl.class);
        assertThat((Object) AopTestUtils.getUltimateTargetObject(storage))
                .isInstanceOf(LocalPrivateStorageAdapter.class);
    }

    @AfterEach
    void removePrivateFixtureBytes() {
        for (String key : storedKeys) {
            storage.delete(key);
        }
    }

    @Test
    void memberBearerCannotSwapAudienceOrForgePlatformTenant() throws Exception {
        String memberToken = issueMemberToken();

        ResponseEntity<String> appBeforeForgery = request(HttpMethod.GET, "/app-api/member/user/get", memberToken,
                null, null);
        assertSuccess(appBeforeForgery);

        // TenantSecurityWebFilter must reject this before an app controller can use the forged tenant.
        ResponseEntity<String> forgedAppRequest = request(HttpMethod.GET, "/app-api/member/user/get", memberToken,
                OTHER_TENANT_ID, null);
        assertGlobalForbidden(forgedAppRequest);

        // TenantContextWebFilter clears the rejected request context; a following token-owned request stays platform.
        ResponseEntity<String> appAfterForgery = request(HttpMethod.GET, "/app-api/member/user/get", memberToken,
                null, null);
        assertSuccess(appAfterForgery);

        ResponseEntity<String> memberAtAdminAudience = request(HttpMethod.GET,
                "/admin-api/system/auth/get-permission-info", memberToken, PLATFORM_TENANT_ID, null);
        assertGlobalForbidden(memberAtAdminAudience);
    }

    @Test
    void adminBearerCannotSwapToAppAudience() throws Exception {
        String operatorToken = issueAdminToken(createAdmin(PLATFORM_TENANT_ID, false));

        ResponseEntity<String> adminAtAppAudience = request(HttpMethod.GET, "/app-api/member/user/get", operatorToken,
                PLATFORM_TENANT_ID, null);

        assertGlobalForbidden(adminAtAppAudience);
    }

    @Test
    void otherTenantAdminCannotBorrowHeaderOrIdentityToReadOrReviewCampusProof() throws Exception {
        ProofFixture fixture = createProofFixture();
        long platformOperatorId = createAdmin(PLATFORM_TENANT_ID, true);
        String platformOperatorToken = issueAdminToken(platformOperatorId);
        insertCampusScope(platformOperatorId, fixture.schoolId(), fixture.campusId(), "[\"VERIFICATION_REVIEW\"]");
        ResponseEntity<String> platformIssue = issue(fixture.assetId(), platformOperatorToken, PLATFORM_TENANT_ID,
                String.valueOf(fixture.verificationId()), "platform reviewer creates the delivery grant");
        assertSuccess(platformIssue);
        String platformDeliveryToken = body(platformIssue).path("data").path("deliveryToken").asText();

        // This caller deliberately has the same exact upstream review permission and an active matching campus
        // scope. A denial below therefore proves the server-owned platform-tenant boundary, not missing RBAC or
        // campus authorization.
        long otherTenantOperatorId = createAdmin(OTHER_TENANT_ID, true);
        String otherTenantToken = issueAdminToken(otherTenantOperatorId);
        insertCampusScope(otherTenantOperatorId, fixture.schoolId(), fixture.campusId(), "[\"VERIFICATION_REVIEW\"]");
        int grantsBefore = grantCount(fixture.assetId());

        ResponseEntity<String> forgedTenantIssue = issue(fixture.assetId(), otherTenantToken, PLATFORM_TENANT_ID,
                String.valueOf(fixture.verificationId()), "attempt through a forged tenant header");
        assertGlobalForbidden(forgedTenantIssue);
        assertThat(grantCount(fixture.assetId())).isEqualTo(grantsBefore);

        // A headerless token-owned request must still not cross into the fixed platform tenant, despite equivalent
        // system RBAC and the exact campus scope.
        ResponseEntity<String> otherTenantIssue = issue(fixture.assetId(), otherTenantToken, null,
                String.valueOf(fixture.verificationId()),
                "attempt with another tenant identity and equivalent authorization");
        assertCampusForbidden(otherTenantIssue);
        assertThat(grantCount(fixture.assetId())).isEqualTo(grantsBefore);

        ResponseEntity<String> forgedTenantReview = review(fixture.verificationId(), otherTenantToken,
                PLATFORM_TENANT_ID);
        assertGlobalForbidden(forgedTenantReview);

        ResponseEntity<String> otherTenantReview = review(fixture.verificationId(), otherTenantToken, null);
        assertCampusForbidden(otherTenantReview);
        assertThat(jdbc.queryForObject("SELECT version FROM campus_verification WHERE id = ?", Long.class,
                fixture.verificationId())).isEqualTo(1L);

        ResponseEntity<byte[]> otherTenantOpen = open(platformDeliveryToken, otherTenantToken, null);
        assertCampusForbiddenBytes(otherTenantOpen);
    }

    @Test
    void platformOperatorNeedsCampusScopeReviewPermissionAndCaseReasonBeforePrivateProofOpens() throws Exception {
        ProofFixture fixture = createProofFixture();
        long operatorId = createAdmin(PLATFORM_TENANT_ID, true);
        String operatorToken = issueAdminToken(operatorId);

        // A current grant for a different campus does not cover this proof's school/campus.
        insertCampusScope(operatorId, fixture.otherSchoolId(), fixture.otherCampusId(), "[\"VERIFICATION_REVIEW\"]");
        ResponseEntity<String> wrongScope = issue(fixture.assetId(), operatorToken, PLATFORM_TENANT_ID,
                String.valueOf(fixture.verificationId()), "scope must match the proof campus");
        assertCampusCode(wrongScope, CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE.getCode());

        // A matching scope with a different operation remains insufficient, even though the upstream role has the
        // exact campus:verification:review permission.
        long matchingScope = insertCampusScope(operatorId, fixture.schoolId(), fixture.campusId(),
                "[\"MEMBERSHIP_REVOKE\"]");
        ResponseEntity<String> missingReviewGrant = issue(fixture.assetId(), operatorToken, PLATFORM_TENANT_ID,
                String.valueOf(fixture.verificationId()), "operator action must be VERIFICATION_REVIEW");
        assertCampusCode(missingReviewGrant, CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE.getCode());

        jdbc.update("UPDATE campus_operator_grant SET actions_json = ? WHERE id = ?",
                "[\"VERIFICATION_REVIEW\"]", matchingScope);
        ResponseEntity<String> missingCase = issue(fixture.assetId(), operatorToken, PLATFORM_TENANT_ID,
                null, "reason remains mandatory");
        assertThat(code(missingCase)).isNotZero();
        assertThat(grantCount(fixture.assetId())).isZero();

        ResponseEntity<String> issued = issue(fixture.assetId(), operatorToken, PLATFORM_TENANT_ID,
                String.valueOf(fixture.verificationId()), "reviewer needs this membership proof");
        assertSuccess(issued);
        String deliveryToken = body(issued).path("data").path("deliveryToken").asText();
        assertThat(deliveryToken).matches("[A-Za-z0-9_-]{43}");

        ResponseEntity<byte[]> opened = open(deliveryToken, operatorToken, PLATFORM_TENANT_ID);
        assertThat(opened.getStatusCode().is2xxSuccessful()).isTrue();
        // The full-server test enables the upstream UTF-8 response filter, which may append a charset
        // parameter even to binary media. Assert the server-owned media type without coupling this
        // boundary test to that harmless servlet decoration.
        assertThat(opened.getHeaders().getContentType()).isNotNull();
        assertThat(opened.getHeaders().getContentType().getType()).isEqualTo(MediaType.IMAGE_PNG.getType());
        assertThat(opened.getHeaders().getContentType().getSubtype()).isEqualTo(MediaType.IMAGE_PNG.getSubtype());
        assertThat(opened.getBody()).containsExactly(fixture.bytes());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM campus_media_access_log
                WHERE asset_id = ? AND decision = 'ALLOW' AND action IN ('ISSUE_DELIVERY', 'OPEN_AUTHORIZED')
                """, Integer.class, fixture.assetId())).isEqualTo(2);
    }

    @Test
    void campusAdminSessionPinsPlatformTenantAndRejectsRefreshTokenAsBearer() throws Exception {
        long operatorId = createAdmin(PLATFORM_TENANT_ID, false);
        String username = "m0203operator" + operatorId;
        String password = "Session-7!";
        jdbc.update("UPDATE system_users SET password = ? WHERE id = ?", passwordEncoder.encode(password), operatorId);

        ResponseEntity<String> loggedIn = request(HttpMethod.POST, "/admin-api/campus/v1/session/login", null,
                OTHER_TENANT_ID, Map.of("username", username, "password", password));
        assertSessionSuccess("login", loggedIn);
        JsonNode loginData = body(loggedIn).path("data");
        assertThat(loginData.path("userId").asText()).isEqualTo(String.valueOf(operatorId));
        assertThat(loginData.path("expiresAt").asText()).isNotBlank();
        String initialAccessToken = loginData.path("accessToken").asText();
        String refreshToken = loginData.path("refreshToken").asText();
        assertThat(initialAccessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM system_oauth2_access_token
                WHERE access_token = ? AND user_id = ? AND user_type = ? AND client_id = 'default' AND tenant_id = ?
                """, Integer.class, initialAccessToken, operatorId, UserTypeEnum.ADMIN.getValue(),
                PLATFORM_TENANT_ID)).isEqualTo(1);

        ResponseEntity<String> refreshed = request(HttpMethod.POST, "/admin-api/campus/v1/session/refresh", null,
                OTHER_TENANT_ID, Map.of("refreshToken", refreshToken));
        assertSessionSuccess("refresh", refreshed);
        JsonNode refreshData = body(refreshed).path("data");
        String currentAccessToken = refreshData.path("accessToken").asText();
        assertThat(refreshData.path("userId").asText()).isEqualTo(String.valueOf(operatorId));
        assertThat(refreshData.path("refreshToken").asText()).isEqualTo(refreshToken);
        assertThat(currentAccessToken).isNotBlank().isNotEqualTo(initialAccessToken);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM system_oauth2_access_token
                WHERE access_token = ? AND user_id = ? AND user_type = ? AND client_id = 'default' AND tenant_id = ?
                """, Integer.class, currentAccessToken, operatorId, UserTypeEnum.ADMIN.getValue(),
                PLATFORM_TENANT_ID)).isEqualTo(1);

        // Upstream OAuth accepts a refresh token as an authentication fallback. The campus wrapper still requires
        // the exact persisted access token for logout, so a refresh credential never becomes a logout bearer.
        ResponseEntity<String> refreshAsBearer = request(HttpMethod.POST, "/admin-api/campus/v1/session/logout",
                refreshToken, OTHER_TENANT_ID, null);
        assertCampusAuthRequired(refreshAsBearer);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_oauth2_access_token WHERE access_token = ?",
                Integer.class, currentAccessToken)).isEqualTo(1);

        ResponseEntity<String> loggedOut = request(HttpMethod.POST, "/admin-api/campus/v1/session/logout",
                currentAccessToken, OTHER_TENANT_ID, null);
        assertSessionSuccess("logout", loggedOut);
        JsonNode logoutBody = body(loggedOut);
        if (!logoutBody.path("data").asBoolean()) {
            throw new AssertionError("logout wire diagnostic: " + logoutWireDiagnostic(loggedOut, logoutBody));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_oauth2_access_token WHERE access_token = ?",
                Integer.class, currentAccessToken)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_oauth2_refresh_token WHERE refresh_token = ?",
                Integer.class, refreshToken)).isZero();
    }

    @Test
    void campusAdminSessionRejectsMemberAndOtherTenantRefreshTokens() throws Exception {
        String memberAccessToken = issueMemberToken();
        String memberRefreshToken = refreshTokenForAccess(memberAccessToken);
        ResponseEntity<String> memberRefresh = request(HttpMethod.POST, "/admin-api/campus/v1/session/refresh", null,
                PLATFORM_TENANT_ID, Map.of("refreshToken", memberRefreshToken));
        assertCampusAuthRequired(memberRefresh);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_oauth2_access_token WHERE access_token = ?",
                Integer.class, memberAccessToken)).isEqualTo(1);

        String otherTenantAccessToken = issueAdminToken(createAdmin(OTHER_TENANT_ID, false));
        String otherTenantRefreshToken = refreshTokenForAccess(otherTenantAccessToken);
        ResponseEntity<String> otherTenantRefresh = request(HttpMethod.POST,
                "/admin-api/campus/v1/session/refresh", null, PLATFORM_TENANT_ID,
                Map.of("refreshToken", otherTenantRefreshToken));
        assertCampusAuthRequired(otherTenantRefresh);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_oauth2_access_token WHERE access_token = ?",
                Integer.class, otherTenantAccessToken)).isEqualTo(1);
    }

    @Test
    void adminQueriesRespectCampusScopeAndReturnOnlyRedactedVerificationFields() throws Exception {
        ProofFixture fixture = createProofFixture();
        long operatorId = createAdmin(PLATFORM_TENANT_ID, true);
        String operatorToken = issueAdminToken(operatorId);
        insertCampusScope(operatorId, fixture.schoolId(), fixture.campusId(), "[\"VERIFICATION_REVIEW\"]");

        ResponseEntity<String> context = request(HttpMethod.GET, "/admin-api/campus/v1/context", operatorToken,
                null, null);
        assertSuccess(context);
        JsonNode contextData = body(context).path("data");
        assertThat(contextData.path("operatorId").asText()).isEqualTo(String.valueOf(operatorId));
        assertThat(contextData.path("authorizationVersion").asLong()).isPositive();
        assertThat(contextData.path("permissions").toString()).contains("campus:verification:review");
        assertThat(contextData.path("schools").toString()).contains(String.valueOf(fixture.schoolId()));

        // The campus filter pins the server-owned platform tenant even when a client forges another header.
        ResponseEntity<String> forgedContext = request(HttpMethod.GET, "/admin-api/campus/v1/context", operatorToken,
                OTHER_TENANT_ID, null);
        assertSuccess(forgedContext);

        ResponseEntity<String> queue = request(HttpMethod.GET,
                "/admin-api/campus/v1/verifications?schoolId=" + fixture.schoolId()
                        + "&campusId=" + fixture.campusId() + "&status=APPROVED&page=1&pageSize=20",
                operatorToken, null, null);
        assertSuccess(queue);
        JsonNode queueData = body(queue).path("data");
        assertThat(queueData.path("total").asInt()).isEqualTo(1);
        assertThat(queueData.path("list").get(0).path("applicationId").asText())
                .isEqualTo(String.valueOf(fixture.verificationId()));

        ResponseEntity<String> forgedQueue = request(HttpMethod.GET,
                "/admin-api/campus/v1/verifications?schoolId=" + fixture.schoolId()
                        + "&campusId=" + fixture.campusId() + "&status=APPROVED&page=1&pageSize=20",
                operatorToken, OTHER_TENANT_ID, null);
        assertSuccess(forgedQueue);

        ResponseEntity<String> detail = request(HttpMethod.GET,
                "/admin-api/campus/v1/verifications/" + fixture.verificationId(), operatorToken, null, null);
        assertSuccess(detail);
        JsonNode detailData = body(detail).path("data");
        assertThat(detailData.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "applicationId", "schoolId", "campusId", "method", "status", "version", "submittedAt", "evidence");
        assertThat(detailData.path("evidence").isArray()).isTrue();
        assertThat(detailData.has("userId")).isFalse();
        assertThat(detailData.has("proofReference")).isFalse();
        assertThat(detailData.has("objectKey")).isFalse();
        assertThat(detailData.has("url")).isFalse();

        ResponseEntity<String> forgedDetail = request(HttpMethod.GET,
                "/admin-api/campus/v1/verifications/" + fixture.verificationId(), operatorToken,
                OTHER_TENANT_ID, null);
        assertSuccess(forgedDetail);

        long otherTenantOperatorId = createAdmin(OTHER_TENANT_ID, true);
        String otherTenantToken = issueAdminToken(otherTenantOperatorId);
        ResponseEntity<String> otherTenantContext = request(HttpMethod.GET, "/admin-api/campus/v1/context",
                otherTenantToken, OTHER_TENANT_ID, null);
        assertGlobalForbidden(otherTenantContext);

        // The global mismatch guard remains strict for non-campus paths.
        ResponseEntity<String> forgedSystemPath = request(HttpMethod.GET,
                "/admin-api/system/auth/get-permission-info", operatorToken, OTHER_TENANT_ID, null);
        assertGlobalForbidden(forgedSystemPath);
    }

    @Test
    void memberVerificationUsesRealMediaBindingsAndSetOrderIdempotency() throws Exception {
        MemberUserDO member = TenantUtils.execute(PLATFORM_TENANT_ID, () -> memberUserService.createUser(
                "proof-submit-" + nextId(), null, "127.0.0.1",
                TerminalEnum.WECHAT_MINI_PROGRAM.getTerminal()));
        String memberToken = issueToken(member.getId(), UserTypeEnum.MEMBER.getValue(), PLATFORM_TENANT_ID);
        long schoolId = nextId();
        long campusId = nextId();
        insertSchoolAndCampus(schoolId, campusId, "submit-" + schoolId);
        long firstAsset = insertUnboundValidAsset(member.getId());
        long secondAsset = insertUnboundValidAsset(member.getId());
        String key = "m0203-submit-" + UUID.randomUUID();
        int memberVerificationBaseline = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_verification WHERE user_id = ?", Integer.class, member.getId());

        ResponseEntity<String> first = submitVerification(memberToken, schoolId, campusId,
                List.of(firstAsset, secondAsset), key);
        assertSuccess(first);
        String applicationId = body(first).path("data").path("applicationId").asText();
        assertThat(applicationId).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_verification WHERE id = ?", Integer.class,
                Long.parseLong(applicationId))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_media_binding WHERE asset_id IN (?, ?)",
                Integer.class, firstAsset, secondAsset)).isEqualTo(2);

        // The service canonicalizes the evidence set before idempotency hashing; reversing the wire list replays
        // the same application and does not create a second proof or duplicate binding.
        ResponseEntity<String> replay = submitVerification(memberToken, schoolId, campusId,
                List.of(secondAsset, firstAsset), key);
        assertSuccess(replay);
        assertThat(body(replay).path("data").path("applicationId").asText()).isEqualTo(applicationId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_verification WHERE user_id = ?", Integer.class,
                member.getId())).isEqualTo(memberVerificationBaseline + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_media_binding WHERE asset_id IN (?, ?)",
                Integer.class, firstAsset, secondAsset)).isEqualTo(2);

        // A fresh idempotency key must still be rejected at the real evidence adapter boundary when it
        // attempts to claim only a subset of an already-bound proof. Capture every durable row first so
        // the assertion proves this failed request has no partial application, proof, binding, or asset write.
        Map<String, Object> verificationBefore = jdbc.queryForMap("""
                SELECT proof_reference, valid_from, expires_at, evidence_refs_json, status, version
                  FROM campus_verification WHERE id = ?
                """, Long.parseLong(applicationId));
        List<Map<String, Object>> bindingsBefore = jdbc.queryForList("""
                SELECT asset_id, asset_generation, owner_id, purpose, resource_type, resource_id,
                       context_hash, valid_from, expires_at, version
                  FROM campus_media_binding WHERE asset_id IN (?, ?) ORDER BY asset_id
                """, firstAsset, secondAsset);
        List<Map<String, Object>> assetsBefore = mediaAssetSnapshot(firstAsset, secondAsset);
        String subsetKey = "m0203-submit-subset-" + UUID.randomUUID();

        ResponseEntity<String> subset = submitVerification(memberToken, schoolId, campusId,
                List.of(firstAsset), subsetKey);
        assertThat(subset.getStatusCode().value())
                .isEqualTo(CampusErrorCodes.MEDIA_ALREADY_BOUND.getHttpStatusValue());
        assertThat(code(subset)).isEqualTo(CampusErrorCodes.MEDIA_ALREADY_BOUND.getCode());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_verification WHERE user_id = ?", Integer.class,
                member.getId())).isEqualTo(memberVerificationBaseline + 1);
        assertThat(jdbc.queryForMap("""
                SELECT proof_reference, valid_from, expires_at, evidence_refs_json, status, version
                  FROM campus_verification WHERE id = ?
                """, Long.parseLong(applicationId))).isEqualTo(verificationBefore);
        assertThat(jdbc.queryForList("""
                SELECT asset_id, asset_generation, owner_id, purpose, resource_type, resource_id,
                       context_hash, valid_from, expires_at, version
                  FROM campus_media_binding WHERE asset_id IN (?, ?) ORDER BY asset_id
                """, firstAsset, secondAsset)).isEqualTo(bindingsBefore);
        assertThat(mediaAssetSnapshot(firstAsset, secondAsset)).isEqualTo(assetsBefore);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM campus_idempotency
                 WHERE platform_tenant_id = ? AND actor_id = ? AND action = ? AND key_hash = ?
                """, Integer.class, PLATFORM_TENANT_ID, member.getId(), "identity.verification.submit",
                hash(subsetKey))).isZero();
    }

    @Test
    void mediaBindingsRollBackWhenVerificationInsertFails() throws Exception {
        MemberUserDO member = TenantUtils.execute(PLATFORM_TENANT_ID, () -> memberUserService.createUser(
                "proof-rollback-" + nextId(), null, "127.0.0.1",
                TerminalEnum.WECHAT_MINI_PROGRAM.getTerminal()));
        String memberToken = issueToken(member.getId(), UserTypeEnum.MEMBER.getValue(), PLATFORM_TENANT_ID);
        long schoolId = nextId();
        long campusId = nextId();
        insertSchoolAndCampus(schoolId, campusId, "rollback-" + schoolId);
        long assetId = insertUnboundValidAsset(member.getId());
        int verificationBaseline = jdbc.queryForObject("SELECT COUNT(*) FROM campus_verification", Integer.class);
        int bindingBaseline = jdbc.queryForObject("SELECT COUNT(*) FROM campus_media_binding WHERE asset_id = ?",
                Integer.class, assetId);
        doThrow(new DataIntegrityViolationException("forced verification insert failure"))
                .when(verificationRepository).insertVerification(any());

        ResponseEntity<String> failed = submitVerification(memberToken, schoolId, campusId, List.of(assetId),
                "m0203-rollback-" + UUID.randomUUID());
        assertThat(failed.getStatusCode().is5xxServerError()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_verification", Integer.class))
                .isEqualTo(verificationBaseline);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_media_binding WHERE asset_id = ?", Integer.class,
                assetId)).isEqualTo(bindingBaseline);
    }

    private ProofFixture createProofFixture() {
        long schoolId = nextId();
        long campusId = nextId();
        long otherSchoolId = nextId();
        long otherCampusId = nextId();
        insertSchoolAndCampus(schoolId, campusId, "proof-a-" + schoolId);
        insertSchoolAndCampus(otherSchoolId, otherCampusId, "proof-b-" + otherSchoolId);

        MemberUserDO member = TenantUtils.execute(PLATFORM_TENANT_ID, () -> memberUserService.createUser(
                "proof-" + nextId(), null, "127.0.0.1",
                TerminalEnum.WECHAT_MINI_PROGRAM.getTerminal()));
        String proofReference = "proof-" + UUID.randomUUID();
        long verificationId = insertAndGetId("""
                INSERT INTO campus_verification (user_id, school_id, campus_id, method, evidence_refs_json,
                    proof_reference, status, version, submitted_at, reviewed_at, reviewed_by, review_reason,
                    valid_from, expires_at, creator, updater)
                VALUES (?, ?, ?, 'STUDENT_CARD', JSON_ARRAY(), ?, 'APPROVED', 1, UTC_TIMESTAMP(3),
                    UTC_TIMESTAMP(3), 1, 'fixture approved', UTC_TIMESTAMP(3),
                    DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY), 'm0203-test', 'm0203-test')
                """, member.getId(), schoolId, campusId, proofReference);

        byte[] bytes = new byte[]{1, 2, 3, 4};
        String objectKey = "m0203-tenant-boundary/" + UUID.randomUUID();
        storage.putImmutable(objectKey, new java.io.ByteArrayInputStream(bytes), bytes.length);
        storedKeys.add(objectKey);
        long assetId = insertAndGetId("""
                INSERT INTO campus_media_asset (owner_id, purpose, original_filename, declared_mime, declared_size,
                    actual_mime, actual_size, content_sha256, object_key, generation, status, idempotency_key_hash,
                    request_hash, ticket_nonce, ticket_expires_at, validated_generation, validated_at, version,
                    creator, updater)
                VALUES (?, 'MEMBERSHIP_VERIFICATION', 'student-card.png', 'image/png', ?, 'image/png', ?,
                    ?, ?, 1, 'VALID', ?, ?, ?, DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 HOUR), 1,
                    UTC_TIMESTAMP(3), 1, 'm0203-test', 'm0203-test')
                """, member.getId(), bytes.length, bytes.length, sha256(bytes), objectKey, hash("idempotency"),
                hash("request"), UUID.randomUUID().toString().replace("-", ""));
        insertAndGetId("""
                INSERT INTO campus_media_binding (asset_id, asset_generation, owner_id, purpose, resource_type,
                    resource_id, context_hash, valid_from, expires_at, version, creator, updater)
                VALUES (?, 1, ?, 'MEMBERSHIP_VERIFICATION', 'MEMBERSHIP_VERIFICATION_PROOF', ?, ?,
                    DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 MINUTE), DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 5 MINUTE),
                    1, 'm0203-test', 'm0203-test')
                """, assetId, member.getId(), proofReference, hash("binding-context-" + proofReference));
        return new ProofFixture(assetId, verificationId, schoolId, campusId, otherSchoolId, otherCampusId, bytes);
    }

    private long createAdmin(long tenantId, boolean withReviewPermission) {
        long userId = nextId();
        jdbc.update("""
                INSERT INTO system_users (id, username, password, nickname, status, creator, updater, tenant_id)
                VALUES (?, ?, '', ?, 0, 'm0203-test', 'm0203-test', ?)
                """, userId, "m0203operator" + userId, "M02.03 operator " + userId, tenantId);
        if (!withReviewPermission) {
            return userId;
        }
        long menuId = insertAndGetId("""
                INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, status, visible,
                    keep_alive, always_show, creator, updater)
                VALUES (?, 'campus:verification:review', 3, 0, 0, '', '#', 0, b'1', b'0', b'0',
                    'm0203-test', 'm0203-test')
                """, "M02.03 verification review " + userId);
        long roleId = insertAndGetId("""
                INSERT INTO system_role (name, code, sort, data_scope, data_scope_dept_ids, status, type, remark,
                    creator, updater, tenant_id)
                VALUES (?, ?, 0, 1, '', 0, 2, 'M02.03 explicit review role', 'm0203-test', 'm0203-test', ?)
                """, "review-" + userId, "m0203_review_" + userId, tenantId);
        TenantUtils.execute(tenantId, () -> {
            permissionService.assignRoleMenu(roleId, Set.of(menuId));
            permissionService.assignUserRole(userId, Set.of(roleId));
        });
        return userId;
    }

    private long insertCampusScope(long operatorId, long schoolId, long campusId, String actionsJson) {
        long scopeId = insertAndGetId("""
                INSERT INTO campus_operator_grant (operator_id, scope_type, school_id, campus_id, actions_json,
                    status, valid_from, expires_at, version, granted_by, creator, updater)
                VALUES (?, 'CAMPUS', ?, ?, ?, 'ACTIVE',
                    DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 MINUTE), DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 5 MINUTE),
                    1, ?, 'm0203-test', 'm0203-test')
                """, operatorId, schoolId, campusId, actionsJson, operatorId);
        jdbc.update("INSERT IGNORE INTO campus_operator_authorization_version (operator_id, version) VALUES (?, 1)",
                operatorId);
        return scopeId;
    }

    private void insertSchoolAndCampus(long schoolId, long campusId, String code) {
        jdbc.update("""
                INSERT INTO campus_school (id, code, name, timezone, status, config_version, creator, updater)
                VALUES (?, ?, ?, 'UTC', 'ENABLED', 1, 'm0203-test', 'm0203-test')
                """, schoolId, code, "Tenant boundary " + code);
        jdbc.update("""
                INSERT INTO campus_campus (id, school_id, code, name, timezone, status, config_version, creator, updater)
                VALUES (?, ?, ?, ?, 'UTC', 'ENABLED', 1, 'm0203-test', 'm0203-test')
                """, campusId, schoolId, code + "-campus", "Tenant boundary campus " + code);
    }

    private String issueMemberToken() {
        MemberUserDO member = TenantUtils.execute(PLATFORM_TENANT_ID, () -> memberUserService.createUser(
                "member-" + nextId(), null, "127.0.0.1",
                TerminalEnum.WECHAT_MINI_PROGRAM.getTerminal()));
        return issueToken(member.getId(), UserTypeEnum.MEMBER.getValue(), PLATFORM_TENANT_ID);
    }

    private String issueAdminToken(long userId) {
        long tenantId = jdbc.queryForObject("SELECT tenant_id FROM system_users WHERE id = ?", Long.class, userId);
        return issueToken(userId, UserTypeEnum.ADMIN.getValue(), tenantId);
    }

    private String issueToken(long userId, int userType, long tenantId) {
        return TenantUtils.execute(tenantId, () -> oauth2TokenApi.createAccessToken(new OAuth2AccessTokenCreateReqDTO()
                .setUserId(userId).setUserType(userType).setClientId("default")).getAccessToken());
    }

    private String refreshTokenForAccess(String accessToken) {
        return jdbc.queryForObject("SELECT refresh_token FROM system_oauth2_access_token WHERE access_token = ?",
                String.class, accessToken);
    }

    private ResponseEntity<String> submitVerification(String token, long schoolId, long campusId,
                                                       List<Long> assetIds, String idempotencyKey) {
        HttpHeaders requestHeaders = headers(token, null);
        requestHeaders.set("Idempotency-Key", idempotencyKey);
        Map<String, Object> requestBody = Map.of("schoolId", String.valueOf(schoolId),
                "campusId", String.valueOf(campusId), "method", "STUDENT_CARD",
                "evidenceRefs", assetIds.stream().map(String::valueOf).toList());
        return http.exchange(url("/app-api/campus/v1/verifications"), HttpMethod.POST,
                new HttpEntity<>(requestBody, requestHeaders), String.class);
    }

    private long insertUnboundValidAsset(long ownerId) {
        byte[] bytes = new byte[]{9, 8, 7, 6};
        String objectKey = "m0203-real-proof/" + UUID.randomUUID();
        storage.putImmutable(objectKey, new java.io.ByteArrayInputStream(bytes), bytes.length);
        storedKeys.add(objectKey);
        return insertAndGetId("""
                INSERT INTO campus_media_asset (owner_id, purpose, original_filename, declared_mime, declared_size,
                    actual_mime, actual_size, content_sha256, object_key, generation, status, idempotency_key_hash,
                    request_hash, ticket_nonce, ticket_expires_at, validated_generation, validated_at, version,
                    creator, updater)
                VALUES (?, 'MEMBERSHIP_VERIFICATION', 'student-card.png', 'image/png', ?, 'image/png', ?,
                    ?, ?, 1, 'VALID', ?, ?, ?, DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 HOUR), 1,
                    UTC_TIMESTAMP(3), 1, 'm0203-test', 'm0203-test')
                """, ownerId, bytes.length, bytes.length, sha256(bytes), objectKey, hash("idempotency-" + UUID.randomUUID()),
                hash("request-" + UUID.randomUUID()), UUID.randomUUID().toString().replace("-", ""));
    }

    private List<Map<String, Object>> mediaAssetSnapshot(long... assetIds) {
        String placeholders = java.util.stream.IntStream.range(0, assetIds.length).mapToObj(index -> "?")
                .collect(java.util.stream.Collectors.joining(", "));
        return jdbc.queryForList("""
                SELECT id, owner_id, purpose, original_filename, declared_mime, declared_size, actual_mime,
                       actual_size, content_sha256, object_key, generation, status, idempotency_key_hash,
                       request_hash, ticket_nonce, ticket_expires_at, validated_generation, validated_at, version
                  FROM campus_media_asset WHERE id IN (""" + placeholders + ") ORDER BY id", toObjects(assetIds));
    }

    private static Object[] toObjects(long[] values) {
        Object[] result = new Object[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index];
        }
        return result;
    }

    private ResponseEntity<String> issue(long assetId, String token, Long tenantId, String caseReference, String reason) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        if (caseReference != null) {
            body.put("caseReference", caseReference);
        }
        if (reason != null) {
            body.put("reason", reason);
        }
        return request(HttpMethod.POST, "/admin-api/campus/v1/media/assets/" + assetId + "/downloads", token,
                tenantId, body);
    }

    private ResponseEntity<String> review(long verificationId, String token, Long tenantId) {
        HttpHeaders headers = headers(token, tenantId);
        headers.set("Idempotency-Key", "m0203-cross-tenant-" + UUID.randomUUID());
        return http.exchange(url("/admin-api/campus/v1/verifications/" + verificationId + "/review"),
                HttpMethod.POST, new HttpEntity<>(Map.of("decision", "APPROVE",
                        "reason", "cross-tenant review attempt", "expectedVersion", 1), headers), String.class);
    }

    private ResponseEntity<byte[]> open(String deliveryToken, String token, Long tenantId) {
        HttpHeaders headers = headers(token, tenantId);
        return http.exchange(url("/admin-api/campus/v1/media/downloads/" + deliveryToken), HttpMethod.GET,
                new HttpEntity<Void>(headers), byte[].class);
    }

    private ResponseEntity<String> request(HttpMethod method, String path, String token, Long tenantId, Object body) {
        return http.exchange(url(path), method, new HttpEntity<>(body, headers(token, tenantId)), String.class);
    }

    private HttpHeaders headers(String token, Long tenantId) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantId != null) {
            headers.set(WebFrameworkUtils.HEADER_TENANT_ID, String.valueOf(tenantId));
        }
        return headers;
    }

    private void assertSuccess(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(code(response)).isZero();
    }

    private void assertSessionSuccess(String operation, ResponseEntity<String> response) throws Exception {
        JsonNode payload = body(response);
        if (!response.getStatusCode().is2xxSuccessful() || payload.path("code").asInt() != 0) {
            List<String> fields = new ArrayList<>();
            payload.fieldNames().forEachRemaining(fields::add);
            JsonNode data = payload.path("data");
            String dataType = data.isMissingNode() ? "missing" : data.getNodeType().name().toLowerCase();
            throw new AssertionError(operation + " wire diagnostic: status=" + response.getStatusCode().value()
                    + ", code=" + payload.path("code").asInt()
                    + ", fields=" + fields + ", dataType=" + dataType);
        }
    }

    private String logoutWireDiagnostic(ResponseEntity<String> response, JsonNode payload) {
        List<String> fields = new ArrayList<>();
        payload.fieldNames().forEachRemaining(fields::add);
        JsonNode data = payload.path("data");
        String dataShape = data.isMissingNode() ? "missing" : data.getNodeType().name().toLowerCase();
        String dataValue = data.isMissingNode() || data.isNull() ? "<absent>"
                : data.isValueNode() ? data.toString() : "<complex-redacted>";
        if (dataValue.length() > 80) {
            dataValue = dataValue.substring(0, 80) + "...";
        }
        String message = payload.path("msg").asText(payload.path("message").asText(""));
        if (message.length() > 160) {
            message = message.substring(0, 160) + "...";
        }
        return "status=" + response.getStatusCode().value()
                + ", code=" + payload.path("code").asInt()
                + ", message=" + message
                + ", dataType=" + dataShape
                + ", dataValue=" + dataValue
                + ", traceId=" + payload.path("traceId").asText("")
                + ", bodyFields=" + fields
                + ", bodyLength=" + (response.getBody() == null ? 0 : response.getBody().length());
    }

    private void assertGlobalForbidden(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).isIn(200, 401, 403);
        assertThat(code(response)).isEqualTo(403);
    }

    private void assertCampusForbidden(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(code(response)).isEqualTo(CampusErrorCodes.PERMISSION_DENIED.getCode());
    }

    private void assertCampusAuthRequired(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(CampusErrorCodes.AUTH_REQUIRED.getHttpStatusValue());
        assertThat(code(response)).isEqualTo(CampusErrorCodes.AUTH_REQUIRED.getCode());
    }

    private void assertCampusForbiddenBytes(ResponseEntity<byte[]> response) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(objectMapper.readTree(response.getBody()).path("code").asInt())
                .isEqualTo(CampusErrorCodes.PERMISSION_DENIED.getCode());
    }

    private void assertCampusCode(ResponseEntity<String> response, int expectedCode) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(
                CampusErrorCodes.RESOURCE_NOT_ACCESSIBLE.getHttpStatusValue());
        assertThat(code(response)).isEqualTo(expectedCode);
    }

    private int code(ResponseEntity<String> response) throws Exception {
        return body(response).path("code").asInt();
    }

    private JsonNode body(ResponseEntity<String> response) throws Exception {
        assertThat(response.getBody()).isNotBlank();
        return objectMapper.readTree(response.getBody());
    }

    private int grantCount(long assetId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM campus_media_delivery_grant WHERE asset_id = ?",
                Integer.class, assetId);
    }

    private long insertAndGetId(String sql, Object... arguments) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            return statement;
        }, holder);
        assertThat(holder.getKey()).isNotNull();
        return holder.getKey().longValue();
    }

    private static long nextId() {
        return IDS.incrementAndGet();
    }

    private static String hash(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.dynamic.datasource.master.url",
                () -> required("CAMPUS_M0101_B_MYSQL_URL"));
        registry.add("spring.datasource.dynamic.datasource.master.username",
                () -> required("CAMPUS_M0101_B_MYSQL_USERNAME"));
        registry.add("spring.datasource.dynamic.datasource.master.password",
                () -> required("CAMPUS_M0101_B_MYSQL_PASSWORD"));
        registry.add("spring.datasource.dynamic.datasource.slave.url",
                () -> required("CAMPUS_M0101_B_MYSQL_URL"));
        registry.add("spring.datasource.dynamic.datasource.slave.username",
                () -> required("CAMPUS_M0101_B_MYSQL_USERNAME"));
        registry.add("spring.datasource.dynamic.datasource.slave.password",
                () -> required("CAMPUS_M0101_B_MYSQL_PASSWORD"));
        registry.add("spring.flyway.url", () -> required("CAMPUS_M0101_B_MYSQL_URL"));
        registry.add("spring.flyway.user", () -> required("CAMPUS_M0101_B_MYSQL_USERNAME"));
        registry.add("spring.flyway.password", () -> required("CAMPUS_M0101_B_MYSQL_PASSWORD"));
        registry.add("spring.data.redis.url", () -> required("CAMPUS_M0101_B_VALKEY_URL"));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("M02.03 B requires " + name);
        }
        return value;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private record ProofFixture(long assetId, long verificationId, long schoolId, long campusId,
                                long otherSchoolId, long otherCampusId, byte[] bytes) {
    }
}
