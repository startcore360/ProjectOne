package com.edu360.api.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.TenantUserLookup;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserIdentityLink;
import com.edu360.api.identity.UserIdentityLinkId;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.identity.UsernameKind;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platform.PlatformUserStatus;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

/**
 * Real, end-to-end integration tests for TenantIssuerAuthenticationManagerResolver: the actual
 * local dev stack (docker-compose at the repo root - Postgres with the committed sql/ bundle
 * already applied, and Keycloak with the abc-university/xyz-university/edu360-platform realms
 * already imported), real signed tokens obtained via real password-grant calls, no mocking
 * anywhere in the authentication path.
 *
 * WHY this does not use Testcontainers, even though the original version of this test did: this
 * sandboxed environment blocks raw Docker-socket access from a JVM process (Testcontainers'
 * bootstrap), while shell-invoked `docker` commands work fine - so the already-running
 * docker-compose stack is used directly instead of spinning up fresh containers. Run `docker
 * compose up -d` from the repo root (see dev-up.ps1) before running this test. CI, which has
 * unrestricted Docker access, can switch this back to Testcontainers if preferred - this is a
 * sandbox workaround, not a statement that Testcontainers is the wrong tool.
 *
 * SCOPE NOTE: this proves the application-level resolver logic (realm matching, tenant-state
 * checks, identity-link lookup, principal construction, and when TenantContext is/isn't set).
 * It does NOT prove Postgres RLS itself is enforced - TenantRoutingDataSource, the component
 * that actually runs "SET LOCAL app.tenant_id" on a borrowed connection, is a separate piece
 * this test does not exercise (this test's datasource connects as the "edu360" superuser, not
 * "edu_app", and RLS is never relied on here). Nobody should read a green run of this class as
 * "RLS is enforced."
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.edu360.api")
@EnableJpaRepositories(basePackages = "com.edu360.api")
// @DataJpaTest only auto-configures JPA repositories, not plain @Component beans - TenantUserLookup
// is a real @Component (shared with production code, see its class comment), so it has to be
// imported explicitly to be available in this slice test's context.
@org.springframework.context.annotation.Import(com.edu360.api.identity.TenantUserLookup.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantIssuerAuthenticationManagerResolverIT {

    private static final String KEYCLOAK_BASE_URL = "http://127.0.0.1:8081";
    private static final String POSTGRES_URL = "jdbc:postgresql://127.0.0.1:5433/edu360db";
    private static final String POSTGRES_USER = "edu360";
    private static final String POSTGRES_PASSWORD = "edu360";
    private static final String TEST_PASSWORD = "Test1234!";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES_URL);
        registry.add("spring.datasource.username", () -> POSTGRES_USER);
        registry.add("spring.datasource.password", () -> POSTGRES_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // The real V4.0 schema is already applied to this database by
        // docker/postgres/provision-edu-app.ps1 - Hibernate must never try to create/alter it.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @BeforeAll
    void ensureSchemaAndTestUsersExist() throws Exception {
        ensureSchemaApplied();
        ensureRealmTestUser("abc-university", "dev-admin-cli-secret-abc", "teacher1");
        ensureRealmTestUser("xyz-university", "dev-admin-cli-secret-xyz", "teacher1");
        ensureRealmTestUser("edu360-platform", "dev-admin-cli-secret-platform", "platformadmin1");
    }

    @AfterEach
    void clearTenantContextBetweenTests() {
        // TenantContext is a ThreadLocal, and JUnit may reuse the same thread across test
        // methods - without this, a tenant set by one test could leak into the next one's
        // assertions, exactly the bug TenantContextClearingFilter exists to prevent in prod.
        TenantContext.clear();
    }

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private PlatformUserRepository platformUserRepository;
    @Autowired
    private UserIdentityLinkRepository userIdentityLinkRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private TenantUserLookup tenantUserLookup;

    private KeycloakProperties keycloakProperties() {
        KeycloakProperties props = new KeycloakProperties();
        props.setBaseUrl(KEYCLOAK_BASE_URL);
        props.setPlatformRealm("edu360-platform");
        return props;
    }

    private TenantIssuerAuthenticationManagerResolver resolver() {
        return new TenantIssuerAuthenticationManagerResolver(
                keycloakProperties(), tenantRepository, platformUserRepository, tenantUserLookup);
    }

    // ---------------------------------------------------------------- test fixtures / helpers

    private static void ensureSchemaApplied() throws Exception {
        try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD)) {
            try (Statement check = conn.createStatement()) {
                var rs = check.executeQuery("SELECT to_regclass('app.tenant_local') IS NOT NULL");
                rs.next();
                if (rs.getBoolean(1)) {
                    return; // already applied by docker/postgres/provision-edu-app.ps1
                }
            }
            List<String> files = List.of(
                    "01_schema.sql", "02_rules.sql", "03_bindings.sql",
                    "03b_fk_indexes.sql", "08_v40_rules.sql", "10_v40_rolldown.sql");
            for (String file : files) {
                try (Statement st = conn.createStatement()) {
                    st.execute(readSqlBundleFile(file));
                }
            }
        }
    }

    private static String readSqlBundleFile(String name) throws IOException {
        // Working directory for Maven Surefire is the module root (api/), so the committed
        // bundle at repo-root sql/ is one directory up.
        return Files.readString(Path.of("..", "sql", name), StandardCharsets.UTF_8);
    }

    /** Creates (or reuses, if it already exists from a previous run) a real, enabled Keycloak
     *  user with a known password in the given realm, via that realm's own admin-cli service
     *  account - the same admin API AuthService/KeycloakAdminClient already use in production,
     *  just driven directly here since KeycloakAdminClient is wired to one static realm only. */
    private static void ensureRealmTestUser(String realm, String adminClientSecret, String username) throws Exception {
        String adminToken = fetchClientCredentialsToken(realm, adminClientSecret);
        Map<String, Object> userRepresentation = Map.of(
                "username", username,
                "email", username + "@" + realm + ".test",
                "firstName", "Test",
                "lastName", "User",
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(Map.of("type", "password", "value", TEST_PASSWORD, "temporary", false)));

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create(KEYCLOAK_BASE_URL + "/admin/realms/" + realm + "/users"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(userRepresentation)))
                .build();
        HttpResponse<String> response = HTTP.send(createRequest, HttpResponse.BodyHandlers.ofString());
        // 201 = created just now, 409 = already exists from an earlier run of this test - both
        // are fine, since the password above is set unconditionally either way for a fresh 201,
        // and an existing 409 user from a prior run already has it.
        if (response.statusCode() != 201 && response.statusCode() != 409) {
            throw new IllegalStateException(
                    "Could not provision test user " + username + " in realm " + realm
                            + ": " + response.statusCode() + " " + response.body());
        }
    }

    private static String fetchClientCredentialsToken(String realm, String clientSecret) throws Exception {
        String form = "client_id=edu360-admin-cli&grant_type=client_credentials&client_secret=" + clientSecret;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KEYCLOAK_BASE_URL + "/realms/" + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Admin client-credentials request failed for realm " + realm
                            + ": " + response.statusCode() + " " + response.body());
        }
        Map<?, ?> body = JSON.readValue(response.body(), Map.class);
        return (String) body.get("access_token");
    }

    private UUID seedPlan() {
        UUID planId = UUID.randomUUID();
        UUID system = UUID.randomUUID();
        entityManager.createNativeQuery(
                "INSERT INTO platform.plan (plan_id, plan_name, price_amount, created_by, updated_by) "
                        + "VALUES (?1, ?2, 0, ?3, ?3)")
                .setParameter(1, planId)
                .setParameter(2, "Test Plan " + planId)
                .setParameter(3, system)
                .executeUpdate();
        return planId;
    }

    /** Inserts a fresh tenant row for this one test method. Does NOT need its own cleanup:
     *  @DataJpaTest wraps each test method in a transaction that rolls back afterwards, so this
     *  row (and the unique keycloak_realm it claims) is gone before the next test method runs -
     *  without that, multiple tests seeding "abc-university" tenants would collide on
     *  uq_tenant_keycloak_realm.
     *
     *  Also inserts the matching app.tenant_local row: found by running this test for real and
     *  hitting a genuine foreign-key violation - app.user_account.tenant_id does NOT reference
     *  platform.tenant directly (the schema's own comment explains why: in the real DB-per-
     *  tenant architecture platform.tenant lives in a different database than app.*, so app.*
     *  foreign keys point at this local per-database anchor row instead, copied at tenant
     *  provisioning time). Both rows always need to exist together for an app.* entity FK to
     *  resolve, in production provisioning and in this test's seeding alike. */
    private UUID seedTenant(String realm, String subdomain, TenantState state, IsolationMode isolationMode) {
        deleteAnyExistingTenantForRealm(realm);
        UUID planId = seedPlan();
        UUID tenantId = UUID.randomUUID();
        UUID system = UUID.randomUUID();
        entityManager.createNativeQuery(
                "INSERT INTO platform.tenant (tenant_id, tenant_name, tenant_type, plan_id, state, "
                        + "isolation_mode, subdomain, keycloak_realm, created_by, updated_by) "
                        + "VALUES (?1, ?2, 'STANDALONE_COLLEGE', ?3, ?4, ?5, ?6, ?7, ?8, ?8)")
                .setParameter(1, tenantId)
                .setParameter(2, "Tenant " + subdomain)
                .setParameter(3, planId)
                .setParameter(4, state.name())
                .setParameter(5, isolationMode.name())
                .setParameter(6, subdomain)
                .setParameter(7, realm)
                .setParameter(8, system)
                .executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO app.tenant_local (tenant_id, tenant_type) VALUES (?1, 'STANDALONE_COLLEGE')")
                .setParameter(1, tenantId)
                .executeUpdate();
        entityManager.flush();
        return tenantId;
    }

    /**
     * Deletes any pre-existing tenant for this realm before seeding a fresh one, scoped to this
     * test method's own transaction (rolled back afterwards by @DataJpaTest, so nothing here is
     * actually lost - this only makes it invisible for the duration of this one test).
     *
     * WHY this is needed, found by actually running this suite after live manual dev testing:
     * uq_tenant_keycloak_realm allows only one platform.tenant row per Keycloak realm, and local
     * dev testing (dev-up.ps1 + manually seeded demo tenants for abc-university/xyz-university)
     * now genuinely owns those same three realms outside of any test transaction. Without this,
     * every test in this class would fail on first insert with a real constraint violation the
     * moment any demo data exists for the same realm.
     */
    private void deleteAnyExistingTenantForRealm(String realm) {
        List<?> existing = entityManager.createNativeQuery(
                        "SELECT tenant_id FROM platform.tenant WHERE keycloak_realm = ?1")
                .setParameter(1, realm)
                .getResultList();
        for (Object row : existing) {
            Object tenantId = row;
            entityManager.createNativeQuery("DELETE FROM app.user_identity_link WHERE tenant_id = ?1")
                    .setParameter(1, tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM app.user_account WHERE tenant_id = ?1")
                    .setParameter(1, tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM app.tenant_local WHERE tenant_id = ?1")
                    .setParameter(1, tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM platform.tenant WHERE tenant_id = ?1")
                    .setParameter(1, tenantId).executeUpdate();
        }
        entityManager.flush();
    }

    private void linkUser(UUID tenantId, String realm, String subject, UserType userType) {
        UUID userAccountId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(tenantId, userAccountId))
                .userType(userType)
                .displayName("Test User " + subject)
                .email(subject + "@example.test")
                .status(UserAccountStatus.ACTIVE)
                .createdBy(actor)
                .updatedBy(actor)
                .build();
        userAccountRepository.save(account);

        UserIdentityLink link = UserIdentityLink.builder()
                .id(new UserIdentityLinkId(tenantId, UUID.randomUUID()))
                .userAccountId(userAccountId)
                .identityRealm(realm)
                .identitySubject(subject)
                .usernameKind(UsernameKind.EMAIL)
                .status(IdentityLinkStatus.ACTIVE)
                .createdBy(actor)
                .updatedBy(actor)
                .build();
        userIdentityLinkRepository.save(link);
        entityManager.flush();
    }

    private void seedPlatformUser(String subject) {
        UUID actor = UUID.randomUUID();
        // A unique-per-call email, not the real admin@edu360-platform.local DevAdminSeeder
        // creates for live dev use - uq_platform_user_email would otherwise collide with that
        // real, persistent row the moment any dev seeding has happened in this same database.
        PlatformUser user = PlatformUser.builder()
                .platformUserId(UUID.randomUUID())
                .email("test-admin-" + short8() + "@edu360-platform.local")
                .fullName("Test Platform Admin")
                .platformRole(PlatformRole.PLATFORM_ADMIN)
                .status(PlatformUserStatus.ACTIVE)
                .mfaRequired(true)
                .identitySubject(subject)
                .createdBy(actor)
                .updatedBy(actor)
                .build();
        platformUserRepository.save(user);
        entityManager.flush();
    }

    /** Real password-grant call to Keycloak - same shape as KeycloakAuthClient.login(). Returns
     *  the raw access token and the "sub" claim it carries (read without verification here,
     *  since this helper is only test setup - the resolver itself is what verifies it for real). */
    private record TokenAndSubject(String token, String subject) {
    }

    private static TokenAndSubject fetchRealToken(String realm, String username, String password) throws Exception {
        String form = "client_id=edu360-frontend&grant_type=password"
                + "&username=" + username + "&password=" + password;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KEYCLOAK_BASE_URL + "/realms/" + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Token request failed: " + response.statusCode() + " " + response.body());
        }
        Map<?, ?> body = JSON.readValue(response.body(), Map.class);
        String accessToken = (String) body.get("access_token");
        return new TokenAndSubject(accessToken, subjectOf(accessToken));
    }

    private static String subjectOf(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<?, ?> claims = JSON.readValue(payload, Map.class);
        return (String) claims.get("sub");
    }

    // ---------------------------------------------------------------------------- the 7 cases

    @Test
    void validTenantTokenAuthenticatesAsTenantPrincipalAndSetsTenantContext() throws Exception {
        UUID tenantId = seedTenant("abc-university", "abc1-" + short8(), TenantState.ACTIVE, IsolationMode.SHARED);
        TokenAndSubject token = fetchRealToken("abc-university", "teacher1", TEST_PASSWORD);
        linkUser(tenantId, "abc-university", token.subject(), UserType.STAFF);

        AuthenticationManager manager = resolver().resolve(issuer("abc-university"));
        var authentication = manager.authenticate(new BearerTokenAuthenticationToken(token.token()));

        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        Assertions.assertEquals(tenantId, principal.tenantId());
        Assertions.assertEquals(UserType.STAFF, principal.userType());
        Assertions.assertEquals(tenantId, TenantContext.currentTenantId(),
                "TenantContext must carry the verified token's own tenant");
    }

    @Test
    void validPlatformTokenAuthenticatesAsPlatformPrincipalAndNeverSetsTenantContext() throws Exception {
        TokenAndSubject token = fetchRealToken("edu360-platform", "platformadmin1", TEST_PASSWORD);
        seedPlatformUser(token.subject());

        AuthenticationManager manager = resolver().resolve(issuer("edu360-platform"));
        var authentication = manager.authenticate(new BearerTokenAuthenticationToken(token.token()));

        PlatformPrincipal principal = (PlatformPrincipal) authentication.getPrincipal();
        Assertions.assertEquals(PlatformRole.PLATFORM_ADMIN, principal.platformRole());
        Assertions.assertFalse(TenantContext.isSet(),
                "a platform login must never grant implicit tenant RLS access");
        Assertions.assertNull(TenantContext.currentTenantId());
    }

    @Test
    void unknownRealmIsRejectedWithNoFallback() {
        // xyz-university's Keycloak realm genuinely exists (imported), but this test needs NO
        // platform.tenant row pointing at it - proving "realm exists in Keycloak" is not enough
        // by itself; the tenant directory must also know it. deleteAnyExistingTenantForRealm
        // clears any real tenant live dev testing has seeded for this realm, scoped to this
        // test method's own rolled-back transaction - see that method's own javadoc for why
        // this is necessary at all.
        deleteAnyExistingTenantForRealm("xyz-university");
        Assertions.assertThrows(InvalidBearerTokenException.class,
                () -> resolver().resolve(issuer("xyz-university")));
    }

    @Test
    void suspendedTenantIsRejectedEvenWithAGenuinelyValidToken() {
        UUID tenantId = seedTenant("abc-university", "abcsusp-" + short8(), TenantState.SUSPENDED, IsolationMode.SHARED);

        boolean satisfiesActiveOrTrialFilter = tenantRepository.findById(tenantId)
                .map(t -> t.getState() == TenantState.ACTIVE || t.getState() == TenantState.TRIAL)
                .orElse(false);
        Assertions.assertFalse(satisfiesActiveOrTrialFilter,
                "a SUSPENDED tenant must never satisfy the ACTIVE/TRIAL filter the resolver relies on");

        // The resolver itself: since this is the only abc-university row in this test's own
        // transaction (rolled back after the method, so no earlier test's row is visible here),
        // resolving the realm must now fail - there is no ACTIVE/TRIAL tenant for it to find.
        Assertions.assertThrows(InvalidBearerTokenException.class,
                () -> resolver().resolve(issuer("abc-university")));
    }

    @Test
    void revokedOrMissingIdentityLinkIsRejected() throws Exception {
        seedTenant("abc-university", "abcrev-" + short8(), TenantState.ACTIVE, IsolationMode.SHARED);
        TokenAndSubject token = fetchRealToken("abc-university", "teacher1", TEST_PASSWORD);
        // Deliberately never call linkUser(...) - no identity_link row exists for this subject
        // in this tenant at all.

        AuthenticationManager manager = resolver().resolve(issuer("abc-university"));
        Assertions.assertThrows(InvalidBearerTokenException.class,
                () -> manager.authenticate(new BearerTokenAuthenticationToken(token.token())));
    }

    @Test
    void garbageTokenIsRejected() {
        seedTenant("abc-university", "abcgarb-" + short8(), TenantState.ACTIVE, IsolationMode.SHARED);
        AuthenticationManager manager = resolver().resolve(issuer("abc-university"));
        Assertions.assertThrows(Exception.class,
                () -> manager.authenticate(new BearerTokenAuthenticationToken("not.a.real.jwt")));
    }

    @Test
    void crossTenantTokenNeverResolvesToTheOtherTenantsUser() throws Exception {
        UUID abcTenantId = seedTenant("abc-university", "abcx-" + short8(), TenantState.ACTIVE, IsolationMode.SHARED);
        UUID xyzTenantId = seedTenant("xyz-university", "xyzx-" + short8(), TenantState.ACTIVE, IsolationMode.SHARED);

        TokenAndSubject abcToken = fetchRealToken("abc-university", "teacher1", TEST_PASSWORD);
        TokenAndSubject xyzToken = fetchRealToken("xyz-university", "teacher1", TEST_PASSWORD);
        linkUser(abcTenantId, "abc-university", abcToken.subject(), UserType.STAFF);
        linkUser(xyzTenantId, "xyz-university", xyzToken.subject(), UserType.STAFF);

        var abcAuth = resolver().resolve(issuer("abc-university"))
                .authenticate(new BearerTokenAuthenticationToken(abcToken.token()));
        TenantContext.clear();
        var xyzAuth = resolver().resolve(issuer("xyz-university"))
                .authenticate(new BearerTokenAuthenticationToken(xyzToken.token()));

        TenantPrincipal abcPrincipal = (TenantPrincipal) abcAuth.getPrincipal();
        TenantPrincipal xyzPrincipal = (TenantPrincipal) xyzAuth.getPrincipal();

        Assertions.assertEquals(abcTenantId, abcPrincipal.tenantId());
        Assertions.assertEquals(xyzTenantId, xyzPrincipal.tenantId());
        Assertions.assertNotEquals(abcPrincipal.tenantId(), xyzPrincipal.tenantId());
        Assertions.assertNotEquals(abcPrincipal.userAccountId(), xyzPrincipal.userAccountId());
    }

    private static String issuer(String realm) {
        return KEYCLOAK_BASE_URL + "/realms/" + realm;
    }

    private static String short8() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
