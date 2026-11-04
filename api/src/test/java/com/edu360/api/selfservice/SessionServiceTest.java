package com.edu360.api.selfservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.UserType;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.selfservice.dto.SessionResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

/**
 * Unit tests for SessionService (AUTH_DEVICES_MANAGE, Phase-1/current-session slice) - Keycloak
 * and the tenant repository mocked out, same style as AuthServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private AuditLogService auditLogService;
    @Mock private Jwt jwt;
    @Mock private Tenant tenant;

    private KeycloakProperties keycloakProperties;
    private SessionService sessionService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID PLATFORM_USER_ID = UUID.randomUUID();
    private static final String REALM = "abc-university";

    private SessionService service() {
        keycloakProperties = new KeycloakProperties();
        keycloakProperties.setPlatformRealm("edu360-platform");
        return new SessionService(tenantRepository, keycloakProperties, keycloakAdminClient, auditLogService);
    }

    // --------------------------------------------------------------------------------------- GET

    @Test
    void listCurrentSessions_withSidClaim_returnsOneRevocableSession() {
        sessionService = service();
        when(jwt.getClaimAsString("sid")).thenReturn("session-123");
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(300);
        when(jwt.getIssuedAt()).thenReturn(issuedAt);
        when(jwt.getExpiresAt()).thenReturn(expiresAt);

        List<SessionResponse> sessions = sessionService.listCurrentSessions(jwt);

        assertThat(sessions).hasSize(1);
        SessionResponse session = sessions.get(0);
        assertThat(session.sessionId()).isEqualTo("session-123");
        assertThat(session.current()).isTrue();
        assertThat(session.revocable()).isTrue();
        assertThat(session.issuedAt()).isEqualTo(issuedAt);
        assertThat(session.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void listCurrentSessions_noSidClaim_returnsNonRevocableSession() {
        sessionService = service();
        when(jwt.getClaimAsString("sid")).thenReturn(null);

        List<SessionResponse> sessions = sessionService.listCurrentSessions(jwt);

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).sessionId()).isNull();
        assertThat(sessions.get(0).revocable()).isFalse();
    }

    // ---------------------------------------------------------------------------- revoke (tenant)

    @Test
    void revokeCurrentSession_tenant_looksUpRealmAndCallsKeycloakAdminClient() {
        sessionService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", false);
        when(jwt.getClaimAsString("sid")).thenReturn("session-123");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenant.getKeycloakRealm()).thenReturn(REALM);

        sessionService.revokeCurrentSession(principal, jwt);

        verify(keycloakAdminClient).logoutSession(REALM, "session-123");
        verify(auditLogService).record(AuditEventType.SESSION_REVOKED, null, "Test User", null, null);
    }

    @Test
    void revokeCurrentSession_tenant_noSidClaim_throwsIllegalArgumentWithoutCallingKeycloak() {
        sessionService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", false);
        when(jwt.getClaimAsString("sid")).thenReturn(null);

        assertThatThrownBy(() -> sessionService.revokeCurrentSession(principal, jwt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokeCurrentSession_tenant_tenantGone_throwsResourceNotFound() {
        sessionService = service();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", false);
        when(jwt.getClaimAsString("sid")).thenReturn("session-123");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.revokeCurrentSession(principal, jwt))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------- revoke (platform)

    @Test
    void revokeCurrentSession_platform_usesPlatformRealm() {
        sessionService = service();
        PlatformPrincipal principal = new PlatformPrincipal(PLATFORM_USER_ID, PlatformRole.PLATFORM_ADMIN,
                "admin@edu360-platform.local", false);
        when(jwt.getClaimAsString("sid")).thenReturn("session-456");

        sessionService.revokeCurrentSession(principal, jwt);

        verify(keycloakAdminClient).logoutSession("edu360-platform", "session-456");
        verify(auditLogService).record(AuditEventType.SESSION_REVOKED, null, "admin@edu360-platform.local", null, null);
    }
}
