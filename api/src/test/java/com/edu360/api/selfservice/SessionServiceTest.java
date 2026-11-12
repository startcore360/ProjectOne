package com.edu360.api.selfservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakAdminClient.UserSessionInfo;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.UserType;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.session.PlatformUserDevice;
import com.edu360.api.platformops.session.PlatformUserDeviceRepository;
import com.edu360.api.selfservice.dto.SessionResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

/**
 * SessionService (AUTH_DEVICES_MANAGE, SES-01): every live session listed, one session per
 * browser, only the caller's own sessions can be signed out. Keycloak and repositories mocked.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private AuditLogService auditLogService;
    @Mock private UserDeviceRepository userDeviceRepository;
    @Mock private PlatformUserDeviceRepository platformUserDeviceRepository;
    @Mock private Jwt jwt;

    private SessionService sessionService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID PLATFORM_USER_ID = UUID.randomUUID();
    private static final String REALM = "abc-university";
    private static final String PLATFORM_REALM = "edu360-platform";
    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final String TEST_BROWSER_ID = "0b6f3c1e-1d2a-4c3b-9e8f-7a6b5c4d3e2f";

    private final TenantPrincipal tenantUser =
            new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF, "Test User", false);
    private final PlatformPrincipal platformUser =
            new PlatformPrincipal(PLATFORM_USER_ID, PlatformRole.PLATFORM_ADMIN, "admin@edu360-platform.local", false);

    @BeforeEach
    void setUp() {
        KeycloakProperties keycloakProperties = new KeycloakProperties();
        keycloakProperties.setPlatformRealm(PLATFORM_REALM);
        sessionService = new SessionService(tenantRepository, keycloakProperties, keycloakAdminClient,
                auditLogService, userDeviceRepository, platformUserDeviceRepository);
    }

    private Tenant tenant() {
        return Tenant.builder().tenantId(TENANT_ID).keycloakRealm(REALM).build();
    }

    private UserDevice activeRow(String sid, String deviceClass, String label) {
        return UserDevice.builder()
                .id(new UserDeviceId(TENANT_ID, UUID.randomUUID()))
                .userAccountId(USER_ACCOUNT_ID)
                .deviceClass(deviceClass)
                .deviceLabel(label)
                .keycloakSessionId(sid)
                .registeredAt(Instant.now().minusSeconds(600))
                .build();
    }

    // ------------------------------------------------------------------------------ sign-in

    @Test
    void recordTenantSignIn_newBrowser_savesDeviceRowWithLabelAndHashedBrowserKey() {
        sessionService.recordTenantSignIn(tenant(), USER_ACCOUNT_ID,
                new SessionService.SignInContext("sid-new", TEST_BROWSER_ID, CHROME_WINDOWS, "203.0.113.7"));

        ArgumentCaptor<UserDevice> saved = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(saved.capture());
        UserDevice row = saved.getValue();
        assertThat(row.getKeycloakSessionId()).isEqualTo("sid-new");
        assertThat(row.getDeviceClass()).isEqualTo("COMPUTER");
        assertThat(row.getDeviceLabel()).isEqualTo("Chrome on Windows");
        assertThat(row.getIpAddress()).isEqualTo("203.0.113.7");
        assertThat(row.getBrowserKeyHash()).matches("^[0-9a-f]{64}$").isNotEqualTo(TEST_BROWSER_ID);
        assertThat(row.getId().getTenantId()).isEqualTo(TENANT_ID);
        verify(keycloakAdminClient, never()).logoutSession(anyString(), anyString());
    }

    @Test
    void recordTenantSignIn_sameBrowserAgain_replacesThePreviousSession() {
        UserDevice previous = activeRow("sid-old", "COMPUTER", "Chrome on Windows");
        when(userDeviceRepository.findByIdTenantIdAndUserAccountIdAndBrowserKeyHashAndRevokedAtIsNull(
                eq(TENANT_ID), eq(USER_ACCOUNT_ID), anyString())).thenReturn(Optional.of(previous));

        sessionService.recordTenantSignIn(tenant(), USER_ACCOUNT_ID,
                new SessionService.SignInContext("sid-new", TEST_BROWSER_ID, CHROME_WINDOWS, "203.0.113.7"));

        assertThat(previous.getRevokedReason()).isEqualTo("NEW_LOGIN");
        assertThat(previous.getRevokedAt()).isNotNull();
        verify(keycloakAdminClient).logoutSession(REALM, "sid-old");
    }

    @Test
    void recordTenantSignIn_otherDevicesStaySignedIn_whenNoBrowserKeyMatches() {
        sessionService.recordTenantSignIn(tenant(), USER_ACCOUNT_ID,
                new SessionService.SignInContext("sid-phone", "another-browser-key-123456", "Mozilla/5.0 (iPhone) Safari/604.1", null));

        verify(keycloakAdminClient, never()).logoutSession(anyString(), anyString());
        ArgumentCaptor<UserDevice> saved = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(saved.capture());
        assertThat(saved.getValue().getDeviceClass()).isEqualTo("PHONE");
        assertThat(saved.getValue().getDeviceLabel()).isEqualTo("Safari on iPhone");
    }

    @Test
    void recordTenantSignIn_withoutSessionId_recordsNothing() {
        sessionService.recordTenantSignIn(tenant(), USER_ACCOUNT_ID,
                new SessionService.SignInContext(null, TEST_BROWSER_ID, CHROME_WINDOWS, null));

        verify(userDeviceRepository, never()).save(any());
    }

    @Test
    void recordTenantSignIn_repositoryFailure_neverBreaksSignIn() {
        when(userDeviceRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        sessionService.recordTenantSignIn(tenant(), USER_ACCOUNT_ID,
                new SessionService.SignInContext("sid-new", null, CHROME_WINDOWS, null));
        // no exception = pass
    }

    @Test
    void recordPlatformSignIn_savesPlatformRow() {
        sessionService.recordPlatformSignIn(PLATFORM_USER_ID,
                new SessionService.SignInContext("sid-p", TEST_BROWSER_ID, CHROME_WINDOWS, "198.51.100.4"));

        ArgumentCaptor<PlatformUserDevice> saved = ArgumentCaptor.forClass(PlatformUserDevice.class);
        verify(platformUserDeviceRepository).save(saved.capture());
        assertThat(saved.getValue().getPlatformUserId()).isEqualTo(PLATFORM_USER_ID);
        assertThat(saved.getValue().getKeycloakSessionId()).isEqualTo("sid-p");
        verify(userDeviceRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------------- list

    @Test
    @DisplayName("REG-I04-1: the list shows only your own sessions (Keycloak is asked for this user's sessions by subject)")
    void listSessions_tenant_listsEveryLiveSessionWithDeviceDetails_currentFirst() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
        when(jwt.getSubject()).thenReturn("kc-sub");
        when(jwt.getClaimAsString("sid")).thenReturn("sid-laptop");
        Instant exp = Instant.now().plusSeconds(300);
        when(jwt.getExpiresAt()).thenReturn(exp);
        long now = System.currentTimeMillis();
        when(keycloakAdminClient.listUserSessions(REALM, "kc-sub")).thenReturn(List.of(
                new UserSessionInfo("sid-phone", "10.0.0.1", now - 50_000, now - 2_000),
                new UserSessionInfo("sid-laptop", "10.0.0.1", now - 90_000, now - 9_000),
                new UserSessionInfo("sid-legacy", "10.0.0.1", now - 99_000, now - 60_000)));
        when(userDeviceRepository.findByIdTenantIdAndUserAccountIdAndRevokedAtIsNull(TENANT_ID, USER_ACCOUNT_ID))
                .thenReturn(List.of(activeRow("sid-phone", "PHONE", "Safari on iPhone"),
                        activeRow("sid-laptop", "COMPUTER", "Chrome on Windows")));

        List<SessionResponse> sessions = sessionService.listSessions(tenantUser, jwt);

        assertThat(sessions).extracting(SessionResponse::sessionId)
                .containsExactly("sid-laptop", "sid-phone", "sid-legacy");
        assertThat(sessions.get(0).current()).isTrue();
        assertThat(sessions.get(0).expiresAt()).isEqualTo(exp);
        assertThat(sessions.get(0).deviceName()).isEqualTo("Chrome on Windows");
        assertThat(sessions.get(1).deviceType()).isEqualTo("PHONE");
        assertThat(sessions.get(1).expiresAt()).isNull();
        // A session from before device recording existed is still listed, as an unknown device.
        assertThat(sessions.get(2).deviceType()).isEqualTo("UNKNOWN");
        assertThat(sessions.get(2).deviceName()).isNull();
    }

    @Test
    void listSessions_tenant_marksRowsWhoseKeycloakSessionIsGoneAsExpired() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
        when(jwt.getSubject()).thenReturn("kc-sub");
        when(jwt.getClaimAsString("sid")).thenReturn("sid-laptop");
        when(keycloakAdminClient.listUserSessions(REALM, "kc-sub")).thenReturn(List.of(
                new UserSessionInfo("sid-laptop", null, null, null)));
        UserDevice gone = activeRow("sid-timed-out", "PHONE", "Chrome on Android");
        when(userDeviceRepository.findByIdTenantIdAndUserAccountIdAndRevokedAtIsNull(TENANT_ID, USER_ACCOUNT_ID))
                .thenReturn(List.of(gone, activeRow("sid-laptop", "COMPUTER", "Chrome on Windows")));

        List<SessionResponse> sessions = sessionService.listSessions(tenantUser, jwt);

        assertThat(sessions).hasSize(1);
        assertThat(gone.getRevokedReason()).isEqualTo("EXPIRED");
        verify(userDeviceRepository).save(gone);
    }

    @Test
    void listSessions_keycloakUnreachable_fallsBackToCurrentSessionOnly() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
        when(jwt.getSubject()).thenReturn("kc-sub");
        when(jwt.getClaimAsString("sid")).thenReturn("sid-laptop");
        when(keycloakAdminClient.listUserSessions(REALM, "kc-sub")).thenThrow(new IllegalStateException("down"));

        List<SessionResponse> sessions = sessionService.listSessions(tenantUser, jwt);

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).sessionId()).isEqualTo("sid-laptop");
        assertThat(sessions.get(0).current()).isTrue();
    }

    @Test
    void listSessions_platform_usesPlatformRealmAndPlatformRows() {
        when(jwt.getSubject()).thenReturn("kc-plat");
        when(jwt.getClaimAsString("sid")).thenReturn("sid-p1");
        when(keycloakAdminClient.listUserSessions(PLATFORM_REALM, "kc-plat")).thenReturn(List.of(
                new UserSessionInfo("sid-p1", null, null, null), new UserSessionInfo("sid-p2", null, null, null)));
        when(platformUserDeviceRepository.findByPlatformUserIdAndRevokedAtIsNull(PLATFORM_USER_ID)).thenReturn(List.of());

        List<SessionResponse> sessions = sessionService.listSessions(platformUser, jwt);

        assertThat(sessions).hasSize(2);
        verify(userDeviceRepository, never()).findByIdTenantIdAndUserAccountIdAndRevokedAtIsNull(any(), any());
    }

    @Test
    void listSessions_lastActiveIsTheLatestApiCall_whenNewerThanKeycloaksRecord() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
        when(jwt.getSubject()).thenReturn("kc-sub");
        when(jwt.getClaimAsString("sid")).thenReturn("sid-laptop");
        long now = System.currentTimeMillis();
        when(keycloakAdminClient.listUserSessions(REALM, "kc-sub")).thenReturn(List.of(
                new UserSessionInfo("sid-laptop", null, now - 600_000, now - 600_000),
                new UserSessionInfo("sid-phone", null, now - 900_000, now - 900_000)));
        UserDevice phone = activeRow("sid-phone", "PHONE", "Safari on iPhone");
        Instant apiCall = Instant.ofEpochMilli(now - 30_000);
        phone.setLastSeenAt(apiCall);
        when(userDeviceRepository.findByIdTenantIdAndUserAccountIdAndRevokedAtIsNull(TENANT_ID, USER_ACCOUNT_ID))
                .thenReturn(List.of(phone));

        List<SessionResponse> sessions = sessionService.listSessions(tenantUser, jwt);

        assertThat(sessions.get(1).sessionId()).isEqualTo("sid-phone");
        assertThat(sessions.get(1).lastActiveAt()).isEqualTo(apiCall);
    }

    // ---------------------------------------------------------------------------- activity

    @Test
    void recordActivity_tenant_updatesLastSeenOnThatSessionsRow() {
        UserDevice row = activeRow("sid-laptop", "COMPUTER", "Chrome on Windows");
        when(userDeviceRepository.findByIdTenantIdAndKeycloakSessionId(TENANT_ID, "sid-laptop")).thenReturn(Optional.of(row));

        sessionService.recordActivity(tenantUser, "sid-laptop");

        assertThat(row.getLastSeenAt()).isNotNull();
        verify(userDeviceRepository).save(row);
    }

    @Test
    void recordActivity_platform_ignoresAnotherUsersRow() {
        PlatformUserDevice other = PlatformUserDevice.builder()
                .platformUserDeviceId(UUID.randomUUID()).platformUserId(UUID.randomUUID())
                .keycloakSessionId("sid-x").build();
        when(platformUserDeviceRepository.findByKeycloakSessionId("sid-x")).thenReturn(Optional.of(other));

        sessionService.recordActivity(platformUser, "sid-x");

        verify(platformUserDeviceRepository, never()).save(any());
    }

    @Test
    void recordActivity_failure_isSwallowed() {
        when(userDeviceRepository.findByIdTenantIdAndKeycloakSessionId(TENANT_ID, "sid-laptop"))
                .thenThrow(new IllegalStateException("db down"));

        sessionService.recordActivity(tenantUser, "sid-laptop");
        // no exception = pass
    }

    @Test
    void isSessionSignedOut_trueOnlyForARevokedRow() {
        UserDevice revoked = activeRow("sid-gone", "PHONE", "Safari on iPhone");
        revoked.setRevokedAt(Instant.now());
        revoked.setRevokedReason("USER");
        when(userDeviceRepository.findByIdTenantIdAndKeycloakSessionId(TENANT_ID, "sid-gone")).thenReturn(Optional.of(revoked));
        when(userDeviceRepository.findByIdTenantIdAndKeycloakSessionId(TENANT_ID, "sid-live"))
                .thenReturn(Optional.of(activeRow("sid-live", "COMPUTER", "Chrome on Windows")));
        when(userDeviceRepository.findByIdTenantIdAndKeycloakSessionId(TENANT_ID, "sid-legacy")).thenReturn(Optional.empty());

        assertThat(sessionService.isSessionSignedOut(tenantUser, "sid-gone")).isTrue();
        assertThat(sessionService.isSessionSignedOut(tenantUser, "sid-live")).isFalse();
        // Signed in before device recording: no row, never blocked.
        assertThat(sessionService.isSessionSignedOut(tenantUser, "sid-legacy")).isFalse();
    }

    @Test
    void isSessionSignedOut_platform_ignoresAnotherUsersRow() {
        PlatformUserDevice other = PlatformUserDevice.builder()
                .platformUserDeviceId(UUID.randomUUID()).platformUserId(UUID.randomUUID())
                .keycloakSessionId("sid-x").revokedAt(Instant.now()).revokedReason("USER").build();
        when(platformUserDeviceRepository.findByKeycloakSessionId("sid-x")).thenReturn(Optional.of(other));

        assertThat(sessionService.isSessionSignedOut(platformUser, "sid-x")).isFalse();
    }

    // ------------------------------------------------------------------------------ revoke

    @Test
    @DisplayName("ACT-SESSION_REVOKE-ALLOW: revoking your own other session ends it (controller answers 204)")
    void revokeSession_ownOtherDevice_endsItMarksRowAndAudits() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
        when(jwt.getSubject()).thenReturn("kc-sub");
        when(jwt.getClaimAsString("sid")).thenReturn("sid-laptop");
        when(keycloakAdminClient.listUserSessions(REALM, "kc-sub")).thenReturn(List.of(
                new UserSessionInfo("sid-laptop", null, null, null), new UserSessionInfo("sid-phone", null, null, null)));
        UserDevice phone = activeRow("sid-phone", "PHONE", "Safari on iPhone");
        when(userDeviceRepository.findByIdTenantIdAndKeycloakSessionId(TENANT_ID, "sid-phone")).thenReturn(Optional.of(phone));

        sessionService.revokeSession(tenantUser, "sid-phone", jwt);

        verify(keycloakAdminClient).logoutSession(REALM, "sid-phone");
        assertThat(phone.getRevokedReason()).isEqualTo("USER");
        verify(auditLogService).record(AuditEventType.SESSION_REVOKED, null, "Test User", null, null);
    }

    @Test
    @DisplayName("ACT-SESSION_REVOKE-DENY: another user's session id gives 404 and nothing is ended")
    void revokeSession_someoneElsesSession_is404AndNothingIsEnded() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
        when(jwt.getSubject()).thenReturn("kc-sub");
        when(jwt.getClaimAsString("sid")).thenReturn("sid-laptop");
        when(keycloakAdminClient.listUserSessions(REALM, "kc-sub")).thenReturn(List.of(
                new UserSessionInfo("sid-laptop", null, null, null)));

        assertThatThrownBy(() -> sessionService.revokeSession(tenantUser, "sid-of-another-user", jwt))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(keycloakAdminClient, never()).logoutSession(anyString(), anyString());
    }

    @Test
    @DisplayName("ACT-SESSION_REVOKE-GUARD: a made-up session id gives 404 and nothing is ended")
    void revokeSession_madeUpId_is404() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
        when(jwt.getSubject()).thenReturn("kc-sub");
        when(keycloakAdminClient.listUserSessions(REALM, "kc-sub")).thenReturn(List.of());

        assertThatThrownBy(() -> sessionService.revokeSession(tenantUser, "00000000-made-up", jwt))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(keycloakAdminClient, never()).logoutSession(anyString(), anyString());
    }

    @Test
    void revokeSession_blankId_is400() {
        assertThatThrownBy(() -> sessionService.revokeSession(tenantUser, " ", jwt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokeSession_platform_ownSession_usesPlatformRealm() {
        when(jwt.getSubject()).thenReturn("kc-plat");
        when(jwt.getClaimAsString("sid")).thenReturn("sid-p1");
        when(keycloakAdminClient.listUserSessions(PLATFORM_REALM, "kc-plat")).thenReturn(List.of(
                new UserSessionInfo("sid-p1", null, null, null), new UserSessionInfo("sid-p2", null, null, null)));

        sessionService.revokeSession(platformUser, "sid-p2", jwt);

        verify(keycloakAdminClient).logoutSession(PLATFORM_REALM, "sid-p2");
        verify(auditLogService).record(AuditEventType.SESSION_REVOKED, null, "admin@edu360-platform.local", null, null);
    }

    @Test
    void revokeCurrentSession_tenant_endsThisSession() {
        when(jwt.getClaimAsString("sid")).thenReturn("session-123");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));

        sessionService.revokeCurrentSession(tenantUser, jwt);

        verify(keycloakAdminClient).logoutSession(REALM, "session-123");
        verify(auditLogService).record(AuditEventType.SESSION_REVOKED, null, "Test User", null, null);
    }

    @Test
    void revokeCurrentSession_noSidClaim_is400WithoutCallingKeycloak() {
        when(jwt.getClaimAsString("sid")).thenReturn(null);

        assertThatThrownBy(() -> sessionService.revokeCurrentSession(tenantUser, jwt))
                .isInstanceOf(IllegalArgumentException.class);
        verify(keycloakAdminClient, never()).logoutSession(anyString(), anyString());
    }

    @Test
    void revokeCurrentSession_tenantGone_is404() {
        when(jwt.getClaimAsString("sid")).thenReturn("session-123");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.revokeCurrentSession(tenantUser, jwt))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ----------------------------------------------------------------------------- sign-out

    @Test
    void signOut_endsCurrentKeycloakSessionWithoutADuplicateAuditEntry() {
        when(jwt.getClaimAsString("sid")).thenReturn("session-123");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));

        sessionService.signOut(tenantUser, jwt);

        verify(keycloakAdminClient).logoutSession(REALM, "session-123");
        verify(auditLogService, never()).record(eq(AuditEventType.SESSION_REVOKED), any(), any(), any(), any());
    }

    @Test
    void signOut_keycloakFailure_isSwallowed() {
        when(jwt.getClaimAsString("sid")).thenReturn("sid-p1");
        org.mockito.Mockito.doThrow(new IllegalStateException("down"))
                .when(keycloakAdminClient).logoutSession(PLATFORM_REALM, "sid-p1");

        sessionService.signOut(platformUser, jwt);
        // no exception = pass
    }
}
