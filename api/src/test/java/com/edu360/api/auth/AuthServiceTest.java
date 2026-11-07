package com.edu360.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakAuthClient;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.files.FileObject;
import com.edu360.api.files.FileObjectId;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.StudentProfile;
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
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.RoleLevel;
import com.edu360.api.rbac.RoleOrigin;
import com.edu360.api.rbac.RoleDefinitionStatus;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentId;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.tenant.LoginTenantResolver;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for AuthService's login/change-password logic, with Keycloak and the database
 * mocked out - the real end-to-end behavior (against a real Postgres and real Keycloak) is
 * covered by TenantIssuerAuthenticationManagerResolverIT and the live manual verification done
 * for Step 8. These tests exist to pin down the parts that are hard to exercise live every time:
 * which re-verification identifier gets picked per UsernameKind, and that a password-change
 * response never echoes the stale pre-change mustChangePassword value (a real bug this exact
 * class of test would have caught immediately, instead of needing a live curl call to find).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
    @Mock private HttpServletRequest httpServletRequest;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private RoleDefinitionRepository roleDefinitionRepository;
    @Mock private FileObjectRepository fileObjectRepository;

    private KeycloakProperties keycloakProperties;
    private AuthService authService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ACCOUNT_ID = UUID.randomUUID();
    private static final String REALM = "abc-university";

    @BeforeEach
    void setUp() {
        keycloakProperties = new KeycloakProperties();
        keycloakProperties.setPlatformRealm("edu360-platform");
        authService = new AuthService(loginTenantResolver, tenantRepository, platformUserRepository,
                userAccountRepository, userIdentityLinkRepository, studentProfileRepository,
                tenantUserLookup, keycloakAdminClient, keycloakAuthClient, keycloakProperties,
                auditLogService, userRoleAssignmentRepository, roleDefinitionRepository, fileObjectRepository);
    }

    // WHY this is a separate statement everywhere it's used, never inlined as an argument
    // (e.g. Optional.of(mockTenant())) - found by actually running these tests: Mockito's
    // when()...thenReturn() is stateful per thread, so starting a NEW when() chain (the ones
    // inside this method) while an OUTER when(...).thenReturn(...) call is still being built
    // throws UnfinishedStubbing. Assign to a local variable first, then pass that variable in.
    private Tenant mockTenant() {
        Tenant tenant = org.mockito.Mockito.mock(Tenant.class);
        // lenient: getTenantId() is only actually read by the login() tests (to build the
        // response), not by changeTenantPassword() (which uses principal.tenantId() instead) -
        // strict stubbing correctly flags it as unused in the latter otherwise.
        org.mockito.Mockito.lenient().when(tenant.getTenantId()).thenReturn(TENANT_ID);
        when(tenant.getKeycloakRealm()).thenReturn(REALM);
        return tenant;
    }

    private UserAccount account(String email, String mobile) {
        return UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID))
                .userType(UserType.STAFF)
                .displayName("Test User")
                .email(email)
                .mobile(mobile)
                .status(UserAccountStatus.ACTIVE)
                .createdBy(UUID.randomUUID())
                .updatedBy(UUID.randomUUID())
                .build();
    }

    private UserIdentityLink link(UsernameKind kind) {
        return UserIdentityLink.builder()
                .id(new UserIdentityLinkId(TENANT_ID, UUID.randomUUID()))
                .userAccountId(USER_ACCOUNT_ID)
                .identityRealm(REALM)
                .identitySubject("kc-subject")
                .usernameKind(kind)
                .status(IdentityLinkStatus.ACTIVE)
                .createdBy(UUID.randomUUID())
                .updatedBy(UUID.randomUUID())
                .build();
    }

    // ------------------------------------------------------------------ re-verification identifier

    @Test
    void changeTenantPassword_emailLogin_reVerifiesWithEmail() {
        Tenant tenant = mockTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID)))
                .thenReturn(Optional.of(account("staff@abc.test", null)));
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.of(link(UsernameKind.EMAIL)));
        when(keycloakAuthClient.login(eq(REALM), eq("staff@abc.test"), anyString()))
                .thenReturn(new KeycloakAuthClient.TokenResult("at", "rt", 300, "kc-subject", false));

        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", true);
        authService.changeTenantPassword(principal, "kc-subject", "oldpass", "newSecurePassword123!", "127.0.0.1");

        verify(keycloakAuthClient).login(REALM, "staff@abc.test", "oldpass");
    }

    @Test
    void changeTenantPassword_mobileLogin_reVerifiesWithMobile() {
        Tenant tenant = mockTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID)))
                .thenReturn(Optional.of(account(null, "+911234567890")));
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.of(link(UsernameKind.MOBILE)));
        when(keycloakAuthClient.login(eq(REALM), eq("+911234567890"), anyString()))
                .thenReturn(new KeycloakAuthClient.TokenResult("at", "rt", 300, "kc-subject", false));

        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.GUARDIAN,
                "Test Parent", true);
        authService.changeTenantPassword(principal, "kc-subject", "oldpass", "newSecurePassword123!", "127.0.0.1");

        verify(keycloakAuthClient).login(REALM, "+911234567890", "oldpass");
    }

    @Test
    void changeTenantPassword_studentCodeLogin_reVerifiesWithStudentCode() {
        Tenant tenant = mockTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID)))
                .thenReturn(Optional.of(account(null, null)));
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.of(link(UsernameKind.STUDENT_CODE)));
        StudentProfile profile = org.mockito.Mockito.mock(StudentProfile.class);
        when(profile.getStudentCode()).thenReturn("STU-0042");
        when(studentProfileRepository.findByTenantIdAndUserAccountId(TENANT_ID, USER_ACCOUNT_ID))
                .thenReturn(Optional.of(profile));
        when(keycloakAuthClient.login(eq(REALM), eq("STU-0042"), anyString()))
                .thenReturn(new KeycloakAuthClient.TokenResult("at", "rt", 300, "kc-subject", false));

        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STUDENT,
                "Test Student", true);
        authService.changeTenantPassword(principal, "kc-subject", "oldpass", "newSecurePassword123!", "127.0.0.1");

        verify(keycloakAuthClient).login(REALM, "STU-0042", "oldpass");
    }

    // ------------------------------------------------------------------- response accuracy

    @Test
    void changeTenantPassword_responseNeverEchoesStaleMustChangePassword() {
        Tenant tenant = mockTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ACCOUNT_ID)))
                .thenReturn(Optional.of(account("staff@abc.test", null)));
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.of(link(UsernameKind.EMAIL)));
        when(keycloakAuthClient.login(any(), any(), any()))
                .thenReturn(new KeycloakAuthClient.TokenResult("at", "rt", 300, "kc-subject", false));

        // Principal walks in with mustChangePassword=true (the forced-change case) - the
        // response must say false, since resetPassword just cleared it, not echo the stale value.
        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", true);
        var response = authService.changeTenantPassword(principal, "kc-subject", "old", "newSecurePassword123!", "127.0.0.1");

        assertThat(response.mustChangePassword()).isFalse();
    }

    @Test
    void changePlatformPassword_responseNeverEchoesStaleMustChangePassword() {
        when(keycloakAuthClient.login(any(), any(), any()))
                .thenReturn(new KeycloakAuthClient.TokenResult("at", "rt", 300, "kc-subject", false));

        PlatformPrincipal principal = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PLATFORM_ADMIN,
                "admin@edu360-platform.local", true);
        var response = authService.changePlatformPassword(principal, "kc-subject", "old", "newSecurePassword123!", "127.0.0.1");

        assertThat(response.mustChangePassword()).isFalse();
    }

    // ----------------------------------------------------------------------------------- /me

    @Test
    void currentTenantUser_noActiveAssignment_roleCodeIsNullNotGuessed() {
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        TenantPrincipal principal = new TenantPrincipal(TENANT_ID, USER_ACCOUNT_ID, UserType.STAFF,
                "Test User", false);
        var response = authService.currentTenantUser(principal);

        assertThat(response.roleCode()).isNull();
        assertThat(response.roleName()).isNull();
        assertThat(response.displayName()).isEqualTo("Test User");
    }

    // ------------------------------------------------------------------------------- login

    @Test
    void login_tenantResolution_buildsTenantUserResponse() {
        Tenant tenant = mockTenant();
        when(loginTenantResolver.resolve(httpServletRequest))
                .thenReturn(new LoginTenantResolver.TenantLogin(tenant));
        when(keycloakAuthClient.login(REALM, "teacher1", "pw"))
                .thenReturn(new KeycloakAuthClient.TokenResult("at", "rt", 300, "kc-subject", false));
        UserAccount account = account("teacher1@abc.test", null);
        when(tenantUserLookup.resolveActiveUser(tenant, REALM, "kc-subject"))
                .thenReturn(new TenantUserLookup.ResolvedUser(account, link(UsernameKind.EMAIL)));

        var response = authService.login(httpServletRequest, "teacher1", "pw", "127.0.0.1");

        assertThat(response.user().tenantId()).isEqualTo(TENANT_ID);
        assertThat(response.user().platformRole()).isNull();
        // No UserRoleAssignment stubbed for this user -> resolveRole finds nothing, same as a
        // real not-yet-assigned user; the response must say so honestly, not guess a role.
        assertThat(response.user().roleCode()).isNull();
        // Likewise: no FileObject stubbed -> resolvePhotoUrl finds nothing, photoUrl stays null
        // rather than pointing at a photo that does not exist.
        assertThat(response.user().photoUrl()).isNull();
    }

    @Test
    void login_userWithActiveCleanPhoto_resolvesPhotoUrl() {
        Tenant tenant = mockTenant();
        when(loginTenantResolver.resolve(httpServletRequest))
                .thenReturn(new LoginTenantResolver.TenantLogin(tenant));
        when(keycloakAuthClient.login(REALM, "teacher1", "pw"))
                .thenReturn(new KeycloakAuthClient.TokenResult("at", "rt", 300, "kc-subject", false));
        UserAccount account = account("teacher1@abc.test", null);
        when(tenantUserLookup.resolveActiveUser(tenant, REALM, "kc-subject"))
                .thenReturn(new TenantUserLookup.ResolvedUser(account, link(UsernameKind.EMAIL)));
        when(fileObjectRepository.findFirstByIdTenantIdAndUploadedByAndPurposeAndStatusAndScanStatus(
                TENANT_ID, USER_ACCOUNT_ID, "PROFILE_PHOTO", "ACTIVE", "CLEAN"))
                .thenReturn(Optional.of(FileObject.builder().id(new FileObjectId(TENANT_ID, UUID.randomUUID())).build()));

        var response = authService.login(httpServletRequest, "teacher1", "pw", "127.0.0.1");

        assertThat(response.user().photoUrl()).isEqualTo("/me/profile/photo");
    }

    @Test
    void login_userWithActiveTenantScopeAssignment_resolvesRoleCodeAndName() {
        Tenant tenant = mockTenant();
        when(loginTenantResolver.resolve(httpServletRequest))
                .thenReturn(new LoginTenantResolver.TenantLogin(tenant));
        when(keycloakAuthClient.login(REALM, "teacher1", "pw"))
                .thenReturn(new KeycloakAuthClient.TokenResult("at", "rt", 300, "kc-subject", false));
        UserAccount account = account("teacher1@abc.test", null);
        when(tenantUserLookup.resolveActiveUser(tenant, REALM, "kc-subject"))
                .thenReturn(new TenantUserLookup.ResolvedUser(account, link(UsernameKind.EMAIL)));

        UUID roleDefinitionId = UUID.randomUUID();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, UUID.randomUUID()))
                .userAccountId(USER_ACCOUNT_ID)
                .roleDefinitionId(roleDefinitionId)
                .scopeType(ScopeType.TENANT)
                .effectiveFrom(Instant.now().minusSeconds(60))
                .status(AssignmentStatus.ACTIVE)
                .build();
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, USER_ACCOUNT_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(assignment));
        RoleDefinition roleDefinition = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, roleDefinitionId))
                .roleCode("CLASS_TEACHER")
                .roleName("Class Teacher")
                .roleOrigin(RoleOrigin.STANDARD)
                .roleLevel(RoleLevel.SCHOOL)
                .baseStandardRole("CLASS_TEACHER")
                .status(RoleDefinitionStatus.ACTIVE)
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, roleDefinitionId)))
                .thenReturn(Optional.of(roleDefinition));

        var response = authService.login(httpServletRequest, "teacher1", "pw", "127.0.0.1");

        assertThat(response.user().roleCode()).isEqualTo("CLASS_TEACHER");
        assertThat(response.user().roleName()).isEqualTo("Class Teacher");
    }

    @Test
    void login_platformResolution_buildsPlatformUserResponse() {
        when(loginTenantResolver.resolve(httpServletRequest))
                .thenReturn(new LoginTenantResolver.PlatformLogin("edu360-platform"));
        when(keycloakAuthClient.login("edu360-platform", "admin@edu360-platform.local", "pw"))
                .thenReturn(new KeycloakAuthClient.TokenResult("at", "rt", 300, "kc-subject", false));
        PlatformUser platformUser = PlatformUser.builder()
                .platformUserId(UUID.randomUUID())
                .email("admin@edu360-platform.local")
                .fullName("Platform Admin")
                .platformRole(PlatformRole.PLATFORM_ADMIN)
                .status(PlatformUserStatus.ACTIVE)
                .mfaRequired(true)
                .identitySubject("kc-subject")
                .createdBy(UUID.randomUUID())
                .updatedBy(UUID.randomUUID())
                .build();
        when(platformUserRepository.findByIdentitySubject("kc-subject")).thenReturn(Optional.of(platformUser));

        var response = authService.login(httpServletRequest, "admin@edu360-platform.local", "pw", "127.0.0.1");

        assertThat(response.user().tenantId()).isNull();
        assertThat(response.user().platformRole()).isEqualTo(PlatformRole.PLATFORM_ADMIN);
    }
}
