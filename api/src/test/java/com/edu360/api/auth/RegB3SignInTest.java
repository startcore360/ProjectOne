package com.edu360.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakAdminClient.CredentialInfo;
import com.edu360.api.config.keycloak.KeycloakAdminClient.KeycloakUser;
import com.edu360.api.config.keycloak.KeycloakAuthClient;
import com.edu360.api.config.keycloak.KeycloakAuthClient.PasswordCheck;
import com.edu360.api.config.keycloak.KeycloakAuthClient.TokenResult;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.config.keycloak.KeycloakRealmSecurityService;
import com.edu360.api.exception.AuthChallengeException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.InvalidCredentialsException;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.StudentLoginLookup;
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
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platformops.security.SecurityPolicyService;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.RoleDefinitionStatus;
import com.edu360.api.rbac.RoleLevel;
import com.edu360.api.rbac.RoleOrigin;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentId;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.selfservice.SessionService;
import com.edu360.api.tenant.LoginTenantResolver;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Sprint 1 batch B3 regression tests for sign-in and identity: I-01 (two-step login), I-02 (login
 * types that exist today), I-03 (lockout), I-05 (temporary passwords). The real AuthService and
 * MfaRequirementService run; Keycloak, repositories and the database are mocked and return real
 * rows (role assignments, role definitions, credentials), so the role-to-two-step decision is the
 * production code's. Live Keycloak behaviour is a separate manual check.
 */
@ExtendWith(MockitoExtension.class)
class RegB3SignInTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String REALM = "abc-university";

    @Mock private LoginTenantResolver loginTenantResolver;
    @Mock private TenantRepository tenantRepository;
    @Mock private PlatformUserRepository platformUserRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private UserIdentityLinkRepository userIdentityLinkRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private TenantUserLookup tenantUserLookup;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private KeycloakAuthClient keycloakAuthClient;
    @Mock private AuditLogService auditLogService;
    @Mock private HttpServletRequest httpRequest;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private RoleDefinitionRepository roleDefinitionRepository;
    @Mock private FileObjectRepository fileObjectRepository;
    @Mock private SessionService sessionService;
    @Mock private KeycloakRealmSecurityService realmSecurityService;
    @Mock private StudentLoginLookup studentLoginLookup;

    private AuthService authService;
    private MfaRequirementService mfaService;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        KeycloakProperties props = new KeycloakProperties();
        props.setPlatformRealm("edu360-platform");
        props.setPublicBaseUrl("https://keycloak.test");
        SecurityPolicyService policy = new SecurityPolicyService(null);
        mfaService = new MfaRequirementService(keycloakAdminClient, userIdentityLinkRepository,
                userRoleAssignmentRepository, roleDefinitionRepository);
        authService = new AuthService(loginTenantResolver, tenantRepository, platformUserRepository,
                userAccountRepository, userIdentityLinkRepository, studentProfileRepository, tenantUserLookup,
                keycloakAdminClient, keycloakAuthClient, props, auditLogService, userRoleAssignmentRepository,
                roleDefinitionRepository, fileObjectRepository, sessionService, new PasswordPolicyService(policy),
                mfaService, realmSecurityService, policy, studentLoginLookup);
        tenant = mock(Tenant.class);
        lenient().when(tenant.getTenantId()).thenReturn(TENANT_ID);
        lenient().when(tenant.getKeycloakRealm()).thenReturn(REALM);
        lenient().when(loginTenantResolver.resolve(httpRequest)).thenReturn(new LoginTenantResolver.TenantLogin(tenant));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ helpers

    private static KeycloakUser kcUser(String username, boolean mfaAttribute) {
        return new KeycloakUser("kc-subject", username, true,
                mfaAttribute ? Map.of("mfa_required", List.of("true")) : Map.of(), List.of());
    }

    private static List<CredentialInfo> passwordOnly() {
        return List.of(new CredentialInfo("password", Instant.now().minusSeconds(3600)));
    }

    private static List<CredentialInfo> passwordAndOtp() {
        return List.of(new CredentialInfo("password", Instant.now().minusSeconds(3600)),
                new CredentialInfo("otp", Instant.now().minusSeconds(3000)));
    }

    private UserAccount account(UserType type, Instant credentialExpiresAt) {
        UserAccount account = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ID))
                .userType(type)
                .displayName("Test User")
                .email("u@abc.test")
                .status(UserAccountStatus.ACTIVE)
                .createdBy(UUID.randomUUID())
                .updatedBy(UUID.randomUUID())
                .build();
        account.setCredentialExpiresAt(credentialExpiresAt);
        return account;
    }

    private UserIdentityLink link(UsernameKind kind) {
        return UserIdentityLink.builder()
                .id(new UserIdentityLinkId(TENANT_ID, UUID.randomUUID()))
                .userAccountId(USER_ID).identityRealm(REALM).identitySubject("kc-subject")
                .usernameKind(kind).status(IdentityLinkStatus.ACTIVE)
                .createdBy(UUID.randomUUID()).updatedBy(UUID.randomUUID()).build();
    }

    /** The user holds exactly this role, as a real assignment row plus real role definition row. */
    private void holdsRole(String roleCode) {
        UUID roleDefinitionId = UUID.randomUUID();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, UUID.randomUUID()))
                .userAccountId(USER_ID).roleDefinitionId(roleDefinitionId).scopeType(ScopeType.TENANT)
                .effectiveFrom(Instant.now().minusSeconds(60)).status(AssignmentStatus.ACTIVE).build();
        lenient().when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, USER_ID, AssignmentStatus.ACTIVE)).thenReturn(List.of(assignment));
        RoleDefinition definition = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, roleDefinitionId)).roleCode(roleCode).roleName(roleCode)
                .roleOrigin(RoleOrigin.STANDARD).roleLevel(RoleLevel.COLLEGE).baseStandardRole(roleCode)
                .status(RoleDefinitionStatus.ACTIVE).build();
        lenient().when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, roleDefinitionId)))
                .thenReturn(Optional.of(definition));
    }

    private void accountResolves(UserAccount account) {
        lenient().when(tenantUserLookup.resolveActiveUser(tenant, REALM, "kc-subject"))
                .thenReturn(new TenantUserLookup.ResolvedUser(account, link(UsernameKind.EMAIL)));
    }

    private void passwordVerified(String username, List<CredentialInfo> credentials) {
        when(keycloakAuthClient.verifyPassword(eq(REALM), eq(username), anyString(), any())).thenReturn(PasswordCheck.VERIFIED);
        when(keycloakAdminClient.findUser(REALM, username)).thenReturn(Optional.of(kcUser(username, false)));
        when(keycloakAdminClient.listCredentials(REALM, "kc-subject")).thenReturn(credentials);
    }

    private static TokenResult tokens(boolean mustChange) {
        return new TokenResult("at", "rt", 300, "kc-subject", mustChange);
    }

    private AuthChallengeException challenge(Throwable t) {
        assertThat(t).isInstanceOf(AuthChallengeException.class);
        return (AuthChallengeException) t;
    }

    // ------------------------------------------------------------------ I-01 two-step login

    @Test
    @DisplayName("REG-I01-1: a required role without a code gives 401 MFA_REQUIRED")
    void regI01_1_requiredRoleWithoutCode() {
        holdsRole("UNIVERSITY_ADMIN");
        accountResolves(account(UserType.STAFF, null));
        passwordVerified("admin@abc.test", passwordAndOtp());

        Throwable thrown = catchThrowable(() -> authService.login(httpRequest, "admin@abc.test", "pw", "127.0.0.1"));

        AuthChallengeException ex = challenge(thrown);
        assertThat(ex.getStatus()).isEqualTo(401);
        assertThat(ex.getCode()).isEqualTo("MFA_REQUIRED");
        verify(keycloakAuthClient, never()).login(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("REG-I01-2: a wrong code gives 401")
    void regI01_2_wrongCode() {
        when(keycloakAuthClient.login(REALM, "admin@abc.test", "pw", "000000"))
                .thenThrow(new InvalidCredentialsException("bad code"));
        when(keycloakAdminClient.findUser(REALM, "admin@abc.test")).thenReturn(Optional.of(kcUser("admin@abc.test", true)));

        Throwable thrown = catchThrowable(
                () -> authService.login(httpRequest, "admin@abc.test", "pw", "000000", "127.0.0.1"));

        AuthChallengeException ex = challenge(thrown);
        assertThat(ex.getStatus()).isEqualTo(401);
        assertThat(ex.getCode()).isEqualTo("INVALID_CREDENTIALS");
        verify(auditLogService).record(eq(AuditEventType.LOGIN_FAILURE), any(), eq("admin@abc.test"), any(), any());
    }

    @Test
    @DisplayName("REG-I01-3: the right code gives 200 with tokens")
    void regI01_3_rightCode() {
        holdsRole("UNIVERSITY_ADMIN");
        accountResolves(account(UserType.STAFF, null));
        when(keycloakAuthClient.login(REALM, "admin@abc.test", "pw", "123456")).thenReturn(tokens(false));
        when(keycloakAdminClient.getUser(REALM, "kc-subject")).thenReturn(Optional.of(kcUser("admin@abc.test", true)));
        when(keycloakAdminClient.listCredentials(REALM, "kc-subject")).thenReturn(passwordAndOtp());

        var response = authService.login(httpRequest, "admin@abc.test", "pw", "123456", "127.0.0.1");

        assertThat(response.accessToken()).isEqualTo("at");
        assertThat(response.user().roleCode()).isEqualTo("UNIVERSITY_ADMIN");
    }

    @Test
    @DisplayName("REG-I01-4: a role not in the list signs in without a code")
    void regI01_4_roleNotInListNeedsNoCode() {
        holdsRole("CLASS_TEACHER");
        accountResolves(account(UserType.STAFF, null));
        passwordVerified("t@abc.test", passwordOnly());
        when(keycloakAuthClient.login(REALM, "t@abc.test", "pw")).thenReturn(tokens(false));

        var response = authService.login(httpRequest, "t@abc.test", "pw", "127.0.0.1");

        assertThat(response.accessToken()).isEqualTo("at");
    }

    @Test
    @DisplayName("REG-I01-5: a required role with no authenticator set up gives MFA_ENROLMENT_REQUIRED")
    void regI01_5_noAuthenticatorSetUp() {
        holdsRole("COLLEGE_ADMIN");
        accountResolves(account(UserType.STAFF, null));
        passwordVerified("ca@abc.test", passwordOnly());

        Throwable thrown = catchThrowable(() -> authService.login(httpRequest, "ca@abc.test", "pw", "127.0.0.1"));

        AuthChallengeException ex = challenge(thrown);
        assertThat(ex.getStatus()).isEqualTo(401);
        assertThat(ex.getCode()).isEqualTo("MFA_ENROLMENT_REQUIRED");
        assertThat(ex.getDetails().get("enrolmentUrl")).isEqualTo("https://keycloak.test/realms/abc-university/account");
        // The sign-in also puts the CONFIGURE_TOTP required action on the user so the set-up page opens.
        verify(keycloakAdminClient).setRequiredAction(REALM, "kc-subject", KeycloakAdminClient.CONFIGURE_TOTP_ACTION, true);
    }

    @Test
    @DisplayName("REG-I01-6: assigning a two-step role sets the attribute; removing it clears it")
    void regI01_6_attributeFollowsRoles() {
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, USER_ID, IdentityLinkStatus.ACTIVE)).thenReturn(Optional.of(link(UsernameKind.EMAIL)));
        when(keycloakAdminClient.listCredentials(REALM, "kc-subject")).thenReturn(passwordOnly());

        holdsRole("REGISTRAR");
        mfaService.syncTenantUser(TENANT_ID, USER_ID);
        verify(keycloakAdminClient).setUserAttribute(REALM, "kc-subject", KeycloakAdminClient.MFA_REQUIRED_ATTRIBUTE, "true");

        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, USER_ID, AssignmentStatus.ACTIVE)).thenReturn(List.of());
        mfaService.syncTenantUser(TENANT_ID, USER_ID);
        verify(keycloakAdminClient).setUserAttribute(REALM, "kc-subject", KeycloakAdminClient.MFA_REQUIRED_ATTRIBUTE, null);
        verify(keycloakAdminClient).setRequiredAction(REALM, "kc-subject", KeycloakAdminClient.CONFIGURE_TOTP_ACTION, false);
    }

    @Test
    @DisplayName("REG-I01-7: every code in the two-step role list is a real role code (A-01)")
    void regI01_7_roleListMatchesRealCodes() throws Exception {
        Path sqlDir = Path.of("..", "sql");
        StringBuilder allSql = new StringBuilder();
        try (Stream<Path> files = Files.list(sqlDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                allSql.append(Files.readString(file));
            }
        }
        for (String code : MfaRequirementService.MFA_REQUIRED_ROLES) {
            assertThat(allSql.toString()).as("role code %s must exist in the sql/ bundle", code).contains("'" + code + "'");
        }
    }

    // ------------------------------------------------------------------ I-02 login types

    @Test
    @DisplayName("REG-I02-1: staff email and password signs in")
    void regI02_1_staffValid() {
        accountResolves(account(UserType.STAFF, null));
        passwordVerified("staff@abc.test", passwordOnly());
        when(keycloakAuthClient.login(REALM, "staff@abc.test", "pw")).thenReturn(tokens(false));

        assertThat(authService.login(httpRequest, "staff@abc.test", "pw", "127.0.0.1").user().tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("REG-I02-2: staff wrong password gives 401")
    void regI02_2_staffWrongPassword() {
        when(keycloakAuthClient.verifyPassword(eq(REALM), eq("staff@abc.test"), anyString(), any())).thenReturn(PasswordCheck.INVALID);
        when(keycloakAdminClient.findUser(REALM, "staff@abc.test")).thenReturn(Optional.of(kcUser("staff@abc.test", false)));

        AuthChallengeException ex = challenge(catchThrowable(
                () -> authService.login(httpRequest, "staff@abc.test", "wrong", "127.0.0.1")));

        assertThat(ex.getStatus()).isEqualTo(401);
        assertThat(ex.getCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    private void studentIdResolvesToKeycloakUser() {
        when(studentLoginLookup.accountForStudentCode("S2024001")).thenReturn(Optional.of(USER_ID));
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, USER_ID, IdentityLinkStatus.ACTIVE)).thenReturn(Optional.of(link(UsernameKind.STUDENT_CODE)));
        when(keycloakAdminClient.getUser(REALM, "kc-subject")).thenReturn(Optional.of(kcUser("stu.kc.user", false)));
    }

    @Test
    @DisplayName("REG-I02-3: college student ID and password signs in")
    void regI02_3_studentIdValid() {
        studentIdResolvesToKeycloakUser();
        accountResolves(account(UserType.STUDENT, null));
        passwordVerified("stu.kc.user", passwordOnly());
        when(keycloakAuthClient.login(REALM, "stu.kc.user", "pw")).thenReturn(tokens(false));

        var response = authService.login(httpRequest, "S2024001", "pw", "127.0.0.1");

        assertThat(response.user().userType()).isEqualTo(UserType.STUDENT);
    }

    @Test
    @DisplayName("REG-I02-4: college student ID with a wrong password gives 401")
    void regI02_4_studentIdWrongPassword() {
        studentIdResolvesToKeycloakUser();
        when(keycloakAuthClient.verifyPassword(eq(REALM), eq("stu.kc.user"), anyString(), any())).thenReturn(PasswordCheck.INVALID);
        when(keycloakAdminClient.findUser(REALM, "stu.kc.user")).thenReturn(Optional.of(kcUser("stu.kc.user", false)));

        AuthChallengeException ex = challenge(catchThrowable(
                () -> authService.login(httpRequest, "S2024001", "wrong", "127.0.0.1")));

        assertThat(ex.getStatus()).isEqualTo(401);
    }

    // REG-I02-5..8 (parent mobile code, applicant code) are NOT written: no SMS or email provider exists,
    // so there is no code to send or check. See the report's questions for the Product Owner.

    // ------------------------------------------------------------------ I-03 lockout

    @Test
    @DisplayName("REG-I03-1: once Keycloak reports the account locked, the next attempt gives 423 LOCKED")
    void regI03_1_lockedAfterTooManyWrongPasswords() {
        when(keycloakAuthClient.verifyPassword(eq(REALM), eq("staff@abc.test"), anyString(), any())).thenReturn(PasswordCheck.INVALID);
        when(keycloakAdminClient.findUser(REALM, "staff@abc.test")).thenReturn(Optional.of(kcUser("staff@abc.test", false)));
        when(keycloakAdminClient.isBruteForceLocked(REALM, "kc-subject")).thenReturn(true);

        AuthChallengeException ex = challenge(catchThrowable(
                () -> authService.login(httpRequest, "staff@abc.test", "wrong", "127.0.0.1")));

        assertThat(ex.getStatus()).isEqualTo(423);
        assertThat(ex.getCode()).isEqualTo("LOCKED");
    }

    private void changePasswordSetup() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ID)))
                .thenReturn(Optional.of(account(UserType.STAFF, null)));
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, USER_ID, IdentityLinkStatus.ACTIVE)).thenReturn(Optional.of(link(UsernameKind.EMAIL)));
        when(keycloakAuthClient.verifyPassword(eq(REALM), eq("u@abc.test"), eq("wrong"), any())).thenReturn(PasswordCheck.INVALID);
    }

    @Test
    @DisplayName("REG-I03-4: a wrong current password on change-password is a failed attempt and counts")
    void regI03_4_changePasswordWrongCurrentCounts() {
        changePasswordSetup();
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.STAFF, "Test User", false);

        assertThatThrownBy(() -> authService.changeTenantPassword(principal, "kc-subject", "wrong", "Str0ng-new-pass!", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);

        // verifyPassword is the Keycloak password grant that records the failure in Keycloak's own
        // brute-force store, so the failed attempt counts towards the same lockout as sign-in.
        verify(keycloakAuthClient).verifyPassword(eq(REALM), eq("u@abc.test"), eq("wrong"), any());
        verify(auditLogService).record(eq(AuditEventType.LOGIN_FAILURE), any(), eq("u@abc.test"), any(),
                contains("Change password: current password refused"));
        verify(keycloakAdminClient, never()).resetPassword(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("REG-I03-4b: a locked account on change-password gives 423 LOCKED")
    void regI03_4b_changePasswordWhenLocked() {
        changePasswordSetup();
        when(keycloakAdminClient.isBruteForceLocked(REALM, "kc-subject")).thenReturn(true);
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ID, UserType.STAFF, "Test User", false);

        AuthChallengeException ex = challenge(catchThrowable(
                () -> authService.changeTenantPassword(principal, "kc-subject", "wrong", "Str0ng-new-pass!", "127.0.0.1")));

        assertThat(ex.getStatus()).isEqualTo(423);
    }

    // ------------------------------------------------------------------ I-05 temporary passwords

    @Test
    @DisplayName("REG-I05-3: first sign-in with a temporary password forces a change")
    void regI05_3_firstSignInForcesChange() {
        accountResolves(account(UserType.STAFF, null));
        passwordVerified("new@abc.test", passwordOnly());
        when(keycloakAuthClient.login(REALM, "new@abc.test", "temp-Pass-1")).thenReturn(tokens(true));

        var response = authService.login(httpRequest, "new@abc.test", "temp-Pass-1", "127.0.0.1");

        assertThat(response.user().mustChangePassword()).isTrue();
    }

    @Test
    @DisplayName("REG-I05-4: a printed student password older than 7 days is refused")
    void regI05_4_printedPasswordExpiresAfterSevenDays() {
        Instant issued = Instant.parse("2026-11-01T00:00:00Z");
        assertThat(PrintedPasswordPolicy.expiryFor(UserType.STUDENT, issued)).isEqualTo(issued.plusSeconds(7 * 24 * 3600L));
        assertThat(PrintedPasswordPolicy.expiryFor(UserType.STAFF, issued)).isNull();

        accountResolves(account(UserType.STUDENT, Instant.now().minusSeconds(60)));
        when(keycloakAuthClient.verifyPassword(eq(REALM), eq("stu@abc.test"), anyString(), any())).thenReturn(PasswordCheck.VERIFIED);
        when(keycloakAdminClient.findUser(REALM, "stu@abc.test")).thenReturn(Optional.of(kcUser("stu@abc.test", false)));

        assertThatThrownBy(() -> authService.login(httpRequest, "stu@abc.test", "printed-pass", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("PRINTED_PASSWORD_EXPIRED"));
        verify(keycloakAuthClient, never()).login(anyString(), anyString(), anyString());
    }
}
