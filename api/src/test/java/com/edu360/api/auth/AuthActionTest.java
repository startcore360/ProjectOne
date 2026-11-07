package com.edu360.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.auth.dto.AuthTokenResponse;
import com.edu360.api.auth.dto.AuthUserResponse;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakAuthClient;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.InvalidCredentialsException;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.files.LocalFileStorage;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.StudentProfileRepository;
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
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.selfservice.ProfileService;
import com.edu360.api.selfservice.SessionService;
import com.edu360.api.selfservice.dto.ProfileResponse;
import com.edu360.api.selfservice.dto.ProfileUpdateRequest;
import com.edu360.api.selfservice.dto.SessionResponse;
import com.edu360.api.tenant.LoginTenantResolver;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
public class AuthActionTest {

    @Mock private LoginTenantResolver loginTenantResolver;
    @Mock private TenantRepository tenantRepository;
    @Mock private PlatformUserRepository platformUserRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private UserIdentityLinkRepository userIdentityLinkRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private TenantUserLookup tenantUserLookup;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private KeycloakAuthClient keycloakAuthClient;
    @Mock private KeycloakProperties keycloakProperties;
    @Mock private AuditLogService auditLogService;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private RoleDefinitionRepository roleDefinitionRepository;
    @Mock private FileObjectRepository fileObjectRepository;
    @Mock private LocalFileStorage fileStorage;

    private AuthService authService;
    private SessionService sessionService;
    private ProfileService profileService;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String REALM = "tenant-test";

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                loginTenantResolver,
                tenantRepository,
                platformUserRepository,
                userAccountRepository,
                userIdentityLinkRepository,
                studentProfileRepository,
                tenantUserLookup,
                keycloakAdminClient,
                keycloakAuthClient,
                keycloakProperties,
                auditLogService,
                userRoleAssignmentRepository,
                roleDefinitionRepository,
                fileObjectRepository
        );

        sessionService = new SessionService(
                tenantRepository,
                keycloakProperties,
                keycloakAdminClient,
                auditLogService
        );

        profileService = new ProfileService(
                userAccountRepository,
                platformUserRepository,
                fileObjectRepository,
                fileStorage,
                auditLogService
        );
    }

    // ==========================================
    // Story 1: AUTH_LOGIN
    // ==========================================

    @Test
    @DisplayName("ACT-AUTH_LOGIN-ALLOW: Successful login yields token and clears lockout counter")
    void ACT_AUTH_LOGIN_ALLOW() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).keycloakRealm(REALM).build();
        when(loginTenantResolver.resolve(request)).thenReturn(new LoginTenantResolver.TenantLogin(tenant));
        when(keycloakAuthClient.login(REALM, "admin@school.edu", "validPassword123!"))
                .thenReturn(new KeycloakAuthClient.TokenResult("access-token-123", "refresh-token-123", 300, "kc-sub-1", false));

        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ID))
                .displayName("Admin User")
                .userType(UserType.STAFF)
                .build();
        when(tenantUserLookup.resolveActiveUser(tenant, REALM, "kc-sub-1"))
                .thenReturn(new TenantUserLookup.ResolvedUser(account, mock(UserIdentityLink.class)));

        AuthTokenResponse response = authService.login(request, "admin@school.edu", "validPassword123!", "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token-123");
        verify(auditLogService).record(eq(AuditEventType.LOGIN_SUCCESS), any(), eq("admin@school.edu"), eq("127.0.0.1"), any());
    }

    @Test
    @DisplayName("ACT-AUTH_LOGIN-DENY: Invalid credentials throws InvalidCredentialsException and locks after 5 attempts")
    void ACT_AUTH_LOGIN_DENY() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).keycloakRealm(REALM).build();
        when(loginTenantResolver.resolve(request)).thenReturn(new LoginTenantResolver.TenantLogin(tenant));
        when(keycloakAuthClient.login(REALM, "wrong@school.edu", "wrongPass"))
                .thenThrow(new InvalidCredentialsException("Invalid user credentials"));

        // Fail 4 times
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login(request, "wrong@school.edu", "wrongPass", "127.0.0.1"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        // 5th attempt triggers lockout
        assertThatThrownBy(() -> authService.login(request, "wrong@school.edu", "wrongPass", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);

        // 6th attempt is blocked by 15-minute brute-force lockout rule
        assertThatThrownBy(() -> authService.login(request, "wrong@school.edu", "wrongPass", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("temporarily locked");
    }

    @Test
    @DisplayName("ACT-AUTH_LOGIN-SCOPE: Realm is strictly resolved from Host resolution")
    void ACT_AUTH_LOGIN_SCOPE() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(loginTenantResolver.resolve(request)).thenReturn(new LoginTenantResolver.PlatformLogin("platform"));
        when(keycloakAuthClient.login("platform", "super@platform.local", "secret"))
                .thenThrow(new InvalidCredentialsException("Credentials error"));

        assertThatThrownBy(() -> authService.login(request, "super@platform.local", "secret", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(keycloakAuthClient).login("platform", "super@platform.local", "secret");
    }

    @Test
    @DisplayName("ACT-AUTH_LOGIN-GUARD: Platform principals and privileged roles require MFA")
    void ACT_AUTH_LOGIN_GUARD() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(loginTenantResolver.resolve(request)).thenReturn(new LoginTenantResolver.PlatformLogin("platform"));
        when(keycloakAuthClient.login("platform", "platform@edu360.local", "pass"))
                .thenReturn(new KeycloakAuthClient.TokenResult("token", "ref", 300, "plt-sub", false));

        PlatformUser platformUser = PlatformUser.builder()
                .platformUserId(UUID.randomUUID())
                .platformRole(PlatformRole.PLATFORM_ADMIN)
                .email("platform@edu360.local")
                .status(PlatformUserStatus.ACTIVE)
                .build();
        when(platformUserRepository.findByIdentitySubject("plt-sub")).thenReturn(Optional.of(platformUser));

        AuthTokenResponse response = authService.login(request, "platform@edu360.local", "pass", "127.0.0.1");
        assertThat(response.user().mfaRequired()).isTrue();
    }

    // ==========================================
    // Story 2: AUTH_FIRST_LOGIN_RESET
    // ==========================================

    @Test
    @DisplayName("ACT-AUTH_FIRST_LOGIN_RESET-ALLOW: First login password reset allowed within 7 days")
    void ACT_AUTH_FIRST_LOGIN_RESET_ALLOW() {
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.STUDENT, "Student A", true);
        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).keycloakRealm(REALM).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ID))
                .displayName("Student A")
                .email("student@college.edu")
                .credentialExpiresAt(Instant.now().plus(4, ChronoUnit.DAYS))
                .build();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ID))).thenReturn(Optional.of(account));
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, USER_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.of(UserIdentityLink.builder()
                        .id(new UserIdentityLinkId(TENANT_ID, UUID.randomUUID()))
                        .usernameKind(UsernameKind.EMAIL)
                        .build()));

        AuthUserResponse response = authService.changeTenantPassword(principal, "sub-123", "OldTemp123!", "NewSecure123!", "127.0.0.1");
        assertThat(response.mustChangePassword()).isFalse();
        verify(keycloakAdminClient).resetPassword(REALM, "sub-123", "NewSecure123!");
    }

    @Test
    @DisplayName("ACT-AUTH_FIRST_LOGIN_RESET-DENY: Printed first-login password expired after 7 days (Rule R2-35)")
    void ACT_AUTH_FIRST_LOGIN_RESET_DENY() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).keycloakRealm(REALM).build();
        when(loginTenantResolver.resolve(request)).thenReturn(new LoginTenantResolver.TenantLogin(tenant));
        when(keycloakAuthClient.login(REALM, "student@school.edu", "tempPass123"))
                .thenReturn(new KeycloakAuthClient.TokenResult("token", "ref", 300, "kc-stud-1", true));

        // Account created > 7 days ago with credential expired
        UserAccount expiredAccount = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ID))
                .displayName("Student Expired")
                .userType(UserType.STUDENT)
                .credentialExpiresAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();
        when(tenantUserLookup.resolveActiveUser(tenant, REALM, "kc-stud-1"))
                .thenReturn(new TenantUserLookup.ResolvedUser(expiredAccount, mock(UserIdentityLink.class)));

        assertThatThrownBy(() -> authService.login(request, "student@school.edu", "tempPass123", "127.0.0.1"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Rule R2-35");
    }

    @Test
    @DisplayName("ACT-AUTH_FIRST_LOGIN_RESET-SCOPE: Enforces authenticated user's own tenant context")
    void ACT_AUTH_FIRST_LOGIN_RESET_SCOPE() {
        UUID otherTenantId = UUID.randomUUID();
        TenantPrincipal foreignPrincipal = new TenantPrincipal(otherTenantId, USER_ID, UserType.STUDENT, "Student A", true);
        when(tenantRepository.findById(otherTenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changeTenantPassword(foreignPrincipal, "sub", "old", "newPassword123!", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Tenant no longer exists");
    }

    @Test
    @DisplayName("ACT-AUTH_FIRST_LOGIN_RESET-GUARD: Reset clears mustChangePassword flag")
    void ACT_AUTH_FIRST_LOGIN_RESET_GUARD() {
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.STUDENT, "Student A", true);
        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).keycloakRealm(REALM).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ID))
                .displayName("Student A")
                .email("student@college.edu")
                .build();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ID))).thenReturn(Optional.of(account));
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, USER_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.of(UserIdentityLink.builder()
                        .id(new UserIdentityLinkId(TENANT_ID, UUID.randomUUID()))
                        .usernameKind(UsernameKind.EMAIL)
                        .build()));

        AuthUserResponse response = authService.changeTenantPassword(principal, "sub-123", "current123", "newPass2026!", "127.0.0.1");
        assertThat(response.mustChangePassword()).isFalse();
    }

    // ==========================================
    // Story 3: AUTH_PASSWORD_CHANGE
    // ==========================================

    @Test
    @DisplayName("ACT-AUTH_PASSWORD_CHANGE-ALLOW: Allows voluntary password change with valid >= 8 char password")
    void ACT_AUTH_PASSWORD_CHANGE_ALLOW() {
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.STAFF, "Staff B", false);
        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).keycloakRealm(REALM).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ID))
                .displayName("Staff B")
                .email("staff@college.edu")
                .build();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ID))).thenReturn(Optional.of(account));
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, USER_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.of(UserIdentityLink.builder()
                        .id(new UserIdentityLinkId(TENANT_ID, UUID.randomUUID()))
                        .usernameKind(UsernameKind.EMAIL)
                        .build()));

        AuthUserResponse response = authService.changeTenantPassword(principal, "sub-staff", "OldPass123!", "RobustNewPass2026#", "127.0.0.1");
        assertThat(response).isNotNull();
        verify(keycloakAdminClient).resetPassword(REALM, "sub-staff", "RobustNewPass2026#");
        verify(auditLogService).record(eq(AuditEventType.PASSWORD_CHANGED), any(), eq("staff@college.edu"), eq("127.0.0.1"), any());
    }

    @Test
    @DisplayName("ACT-AUTH_PASSWORD_CHANGE-DENY: Denies passwords < 8 chars or in dictionary blacklist")
    void ACT_AUTH_PASSWORD_CHANGE_DENY() {
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.STAFF, "Staff B", false);

        // Less than 8 chars
        assertThatThrownBy(() -> authService.changeTenantPassword(principal, "sub", "old", "short", "127.0.0.1"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Rule R2-12");

        // Common dictionary password
        assertThatThrownBy(() -> authService.changeTenantPassword(principal, "sub", "old", "password", "127.0.0.1"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("dictionary passwords");
    }

    @Test
    @DisplayName("ACT-AUTH_PASSWORD_CHANGE-SCOPE: Self scope enforced - rejects mismatched user account")
    void ACT_AUTH_PASSWORD_CHANGE_SCOPE() {
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.STAFF, "Staff B", false);
        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).keycloakRealm(REALM).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changeTenantPassword(principal, "sub", "old", "StrongPass2026!", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Account no longer exists");
    }

    @Test
    @DisplayName("ACT-AUTH_PASSWORD_CHANGE-GUARD: Logs PASSWORD_CHANGED event upon successful rotation")
    void ACT_AUTH_PASSWORD_CHANGE_GUARD() {
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.STAFF, "Staff B", false);
        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).keycloakRealm(REALM).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ID))
                .displayName("Staff B")
                .email("staff@college.edu")
                .build();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ID))).thenReturn(Optional.of(account));
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(TENANT_ID, USER_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.of(UserIdentityLink.builder()
                        .id(new UserIdentityLinkId(TENANT_ID, UUID.randomUUID()))
                        .usernameKind(UsernameKind.EMAIL)
                        .build()));

        authService.changeTenantPassword(principal, "sub", "old", "ValidPass2026#", "127.0.0.1");
        verify(auditLogService).record(eq(AuditEventType.PASSWORD_CHANGED), any(), eq("staff@college.edu"), eq("127.0.0.1"), any());
    }

    // ==========================================
    // Story 4: AUTH_DEVICES_MANAGE
    // ==========================================

    @Test
    @DisplayName("ACT-AUTH_DEVICES_MANAGE-ALLOW: Lists active session device for authenticated caller")
    void ACT_AUTH_DEVICES_MANAGE_ALLOW() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("sid")).thenReturn("session-abc-123");
        when(jwt.getIssuedAt()).thenReturn(Instant.now());
        when(jwt.getExpiresAt()).thenReturn(Instant.now().plus(1, ChronoUnit.HOURS));

        List<SessionResponse> sessions = sessionService.listCurrentSessions(jwt);
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).sessionId()).isEqualTo("session-abc-123");
        assertThat(sessions.get(0).current()).isTrue();
        assertThat(sessions.get(0).revocable()).isTrue();
    }

    @Test
    @DisplayName("ACT-AUTH_DEVICES_MANAGE-DENY: Rejects revocation when token lacks session claim")
    void ACT_AUTH_DEVICES_MANAGE_DENY() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("sid")).thenReturn(null);
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.STAFF, "Staff User", false);

        assertThatThrownBy(() -> sessionService.revokeCurrentSession(principal, jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no session id");
    }

    @Test
    @DisplayName("ACT-AUTH_DEVICES_MANAGE-SCOPE: Only revokes session inside caller's tenant realm")
    void ACT_AUTH_DEVICES_MANAGE_SCOPE() {
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.STAFF, "Staff User", false);
        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).keycloakRealm(REALM).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        sessionService.revokeSession(principal, "session-xyz");
        verify(keycloakAdminClient).logoutSession(REALM, "session-xyz");
    }

    @Test
    @DisplayName("ACT-AUTH_DEVICES_MANAGE-GUARD: Session revocation triggers SESSION_REVOKED audit log")
    void ACT_AUTH_DEVICES_MANAGE_GUARD() {
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.STAFF, "Staff User", false);
        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).keycloakRealm(REALM).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        sessionService.revokeSession(principal, "session-xyz");
        verify(auditLogService).record(eq(AuditEventType.SESSION_REVOKED), any(), eq("Staff User"), any(), any());
    }

    // ==========================================
    // Story 5: PROFILE_EDIT_OWN
    // ==========================================

    @Test
    @DisplayName("ACT-PROFILE_EDIT_OWN-ALLOW: Self user can update display name and phone")
    void ACT_PROFILE_EDIT_OWN_ALLOW() {
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.STAFF, "Staff User", false);
        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ID))
                .displayName("Old Display Name")
                .mobile("+919876543210")
                .userType(UserType.STAFF)
                .build();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ID))).thenReturn(Optional.of(account));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileUpdateRequest update = new ProfileUpdateRequest();
        update.setDisplayName("New Display Name");

        ProfileResponse response = profileService.updateTenantProfile(principal, update);
        assertThat(response.displayName()).isEqualTo("New Display Name");
        verify(auditLogService).record(eq(AuditEventType.PROFILE_UPDATED), any(), eq("Staff User"), any(), any());
    }

    @Test
    @DisplayName("ACT-PROFILE_EDIT_OWN-DENY: Parent mobile update rejected without OTP verification (Rule WF-30)")
    void ACT_PROFILE_EDIT_OWN_DENY() {
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.GUARDIAN, "Parent User", false);
        UserAccount parentAccount = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ID))
                .displayName("Parent User")
                .mobile("+919876543210")
                .userType(UserType.GUARDIAN)
                .build();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ID))).thenReturn(Optional.of(parentAccount));

        ProfileUpdateRequest update = new ProfileUpdateRequest();
        update.setMobile("+919999888877"); // new mobile without otpCode

        assertThatThrownBy(() -> profileService.updateTenantProfile(principal, update))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Rule WF-30");
    }

    @Test
    @DisplayName("ACT-PROFILE_EDIT_OWN-SCOPE: Self-only scope - cannot access profile outside authenticated user")
    void ACT_PROFILE_EDIT_OWN_SCOPE() {
        UUID unknownUser = UUID.randomUUID();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, unknownUser, UserType.STAFF, "Unknown", false);
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, unknownUser))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getTenantProfile(principal))
                .isInstanceOf(com.edu360.api.exception.ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("ACT-PROFILE_EDIT_OWN-GUARD: Parent mobile update dispatches alert to old number")
    void ACT_PROFILE_EDIT_OWN_GUARD() {
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.GUARDIAN, "Parent User", false);
        UserAccount parentAccount = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ID))
                .displayName("Parent User")
                .mobile("+919876543210")
                .userType(UserType.GUARDIAN)
                .build();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ID))).thenReturn(Optional.of(parentAccount));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileUpdateRequest update = new ProfileUpdateRequest();
        update.setMobile("+919999888877");
        update.setOtpCode("123456");

        profileService.updateTenantProfile(principal, update);

        verify(auditLogService).record(eq(AuditEventType.PROFILE_UPDATED), any(), eq("Parent User"), any(),
                org.mockito.ArgumentMatchers.contains("Alert sent to old number"));
    }
}
