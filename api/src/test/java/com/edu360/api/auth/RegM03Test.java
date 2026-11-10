package com.edu360.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.auth.dto.IdentityEventRequest;
import com.edu360.api.config.keycloak.KeycloakProperties;

@ExtendWith(MockitoExtension.class)
public class RegM03Test {

    @Mock
    private AuthService authService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private JwtDecoder jwtDecoder;

    private KeycloakProperties keycloakProperties;
    private IdentityEventTokenValidator tokenValidator;
    private AuthController authController;

    private static final String BASE_URL = "http://localhost:8081";
    private static final String PLATFORM_REALM = "edu360-platform";
    private static final String SERVICE_CLIENT_ID = "edu360-identity-events";
    private static final String ISSUER = BASE_URL + "/realms/" + PLATFORM_REALM;

    @BeforeEach
    void setUp() {
        keycloakProperties = new KeycloakProperties();
        keycloakProperties.setBaseUrl(BASE_URL);
        keycloakProperties.setPlatformRealm(PLATFORM_REALM);
        keycloakProperties.setIdentityEventsClientId(SERVICE_CLIENT_ID);

        tokenValidator = new IdentityEventTokenValidator(keycloakProperties, jwtDecoder);
        authController = new AuthController(authService, auditLogService, tokenValidator);
    }

    private Jwt createJwt(String azp, List<String> realmRoles, Instant expiresAt) {
        Instant issuedAt = expiresAt.minusSeconds(120);
        return Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .claim("azp", azp)
                .claim("realm_access", Map.of("roles", realmRoles))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    @DisplayName("REG-M03-1")
    void REG_M03_1() {
        // no header gives 401
        ResponseEntity<Void> response = authController.handleIdentityEvent(null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("REG-M03-2")
    void REG_M03_2() {
        // a shared secret (the old X-Service-Auth style) in place of a bearer token gives 401;
        // the X-Service-Auth header itself is no longer read at all (REG-M03-9 proves that over HTTP)
        ResponseEntity<Void> response1 = authController.handleIdentityEvent("some-secret", null);
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Void> response2 = authController.handleIdentityEvent("edu360-internal-service-secret-token", null);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("REG-M03-3")
    void REG_M03_3() {
        // a valid Product Owner user token gives 401
        Jwt poJwt = createJwt("edu360-frontend", List.of("PRODUCT_OWNER"), Instant.now().plusSeconds(300));
        when(jwtDecoder.decode("po-user-token")).thenReturn(poJwt);

        ResponseEntity<Void> response = authController.handleIdentityEvent("Bearer po-user-token", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("REG-M03-4")
    void REG_M03_4() {
        // a token from another client gives 401
        Jwt otherClientJwt = createJwt("edu360-admin-cli", List.of("identity-events:write"), Instant.now().plusSeconds(300));
        when(jwtDecoder.decode("other-client-token")).thenReturn(otherClientJwt);

        ResponseEntity<Void> response = authController.handleIdentityEvent("Bearer other-client-token", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("REG-M03-5")
    void REG_M03_5() {
        // an expired service token gives 401
        Jwt expiredJwt = createJwt(SERVICE_CLIENT_ID, List.of("identity-events:write"), Instant.now().minusSeconds(60));
        when(jwtDecoder.decode("expired-token")).thenReturn(expiredJwt);

        ResponseEntity<Void> response = authController.handleIdentityEvent("Bearer expired-token", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("REG-M03-6")
    void REG_M03_6() {
        // a valid service token gives 200, and the lockout is cleared
        Jwt validServiceJwt = createJwt(SERVICE_CLIENT_ID, List.of("identity-events:write"), Instant.now().plusSeconds(300));
        when(jwtDecoder.decode("valid-service-token")).thenReturn(validServiceJwt);

        IdentityEventRequest event = new IdentityEventRequest(
                "LOGIN", PLATFORM_REALM, "user-lockout-clear", "127.0.0.1", "Successful login", Map.of(), Instant.now());

        ResponseEntity<Void> response = authController.handleIdentityEvent("Bearer valid-service-token", event);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authService).clearLockout("user-lockout-clear");
        verify(auditLogService).record(eq(AuditEventType.LOGIN_SUCCESS), any(), eq("user-lockout-clear"), eq("127.0.0.1"), eq("Successful login"));
    }

    @Test
    @DisplayName("REG-M03-7")
    void REG_M03_7() throws IOException {
        // no string literal secret exists in AuthController (architecture test)
        Path path = Path.of("src/main/java/com/edu360/api/auth/AuthController.java");
        if (!Files.exists(path)) {
            path = Path.of("api/src/main/java/com/edu360/api/auth/AuthController.java");
        }
        String content = Files.readString(path);
        assertThat(content)
                .doesNotContain("edu360-internal-service-secret-token")
                .doesNotContain("expectedServiceAuthSecret")
                .doesNotContain("service-auth-secret");
    }
}
