package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserIdentityLink;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.dto.AssignRoleRequest;
import com.edu360.api.rbac.dto.UserRoleAssignmentResponse;

/**
 * Action & Guard tests for ROLE_ASSIGN (Sprint 1 Story #14 / WF-27, MX-04):
 * - ACT-ROLE_ASSIGN-ALLOW
 * - ACT-ROLE_ASSIGN-DENY
 * - ACT-ROLE_ASSIGN-SCOPE
 * - ACT-ROLE_ASSIGN-GUARD (self-elevation, rule 10 single active role, user session logout)
 */
class RoleAssignmentControllerTest {

    private UserRoleAssignmentRepository userRoleAssignmentRepository;
    private RoleDefinitionRepository roleDefinitionRepository;
    private RoleActionRepository roleActionRepository;
    private StandardRoleRepository standardRoleRepository;
    private StandardRoleActionRepository standardRoleActionRepository;
    private UserAccountRepository userAccountRepository;
    private UserIdentityLinkRepository userIdentityLinkRepository;
    private PermissionEvaluatorService permissionEvaluatorService;
    private KeycloakAdminClient keycloakAdminClient;
    private AuditLogService auditLogService;
    private RoleAssignmentService roleAssignmentService;
    private RoleAssignmentController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ADMIN_USER_ID = UUID.randomUUID();
    private static final UUID TARGET_USER_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final UUID ROLE_DEF_ID = UUID.randomUUID();
    private static final UUID ASSIGNMENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userRoleAssignmentRepository = mock(UserRoleAssignmentRepository.class);
        roleDefinitionRepository = mock(RoleDefinitionRepository.class);
        roleActionRepository = mock(RoleActionRepository.class);
        standardRoleRepository = mock(StandardRoleRepository.class);
        standardRoleActionRepository = mock(StandardRoleActionRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        userIdentityLinkRepository = mock(UserIdentityLinkRepository.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        keycloakAdminClient = mock(KeycloakAdminClient.class);
        auditLogService = mock(AuditLogService.class);

        roleAssignmentService = new RoleAssignmentService(
                userRoleAssignmentRepository,
                roleDefinitionRepository,
                roleActionRepository,
                standardRoleRepository,
                standardRoleActionRepository,
                userAccountRepository,
                userIdentityLinkRepository,
                permissionEvaluatorService,
                keycloakAdminClient,
                auditLogService
        );

        when(permissionEvaluatorService.can(any(), eq("ROLE_ASSIGN"), any(), any()))
                .thenReturn(PermissionDecision.allow());

        controller = new RoleAssignmentController(roleAssignmentService);
    }

    private Authentication authenticatedTenantUser(UUID tenantId, UUID userId) {
        TenantPrincipal principal = new TenantPrincipal(
                tenantId,
                userId,
                UserType.STAFF,
                "University Admin",
                false
        );
        return new UsernamePasswordAuthenticationToken(principal, "n/a", Collections.emptyList());
    }

    @Test
    @DisplayName("ACT-ROLE_ASSIGN-ALLOW: Authorized Admin gives role to user, ends previous assignment, and invalidates sessions")
    void actRoleAssign_allow() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        AssignRoleRequest request = new AssignRoleRequest(
                ROLE_DEF_ID, null, ScopeType.INSTITUTION, INSTITUTION_ID, null, null, null
        );

        // Target user exists
        UserAccount targetAccount = mock(UserAccount.class);
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, TARGET_USER_ID)))
                .thenReturn(Optional.of(targetAccount));

        // Role definition exists
        RoleDefinition roleDefinition = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID))
                .roleCode("TEACHER")
                .roleName("Teacher")
                .roleOrigin(RoleOrigin.STANDARD)
                .roleLevel(RoleLevel.COLLEGE)
                .status(RoleDefinitionStatus.ACTIVE)
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID)))
                .thenReturn(Optional.of(roleDefinition));

        // Previous active assignment in exact same scope exists (Rule 10)
        UserRoleAssignment previousAssignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, UUID.randomUUID()))
                .userAccountId(TARGET_USER_ID)
                .roleDefinitionId(UUID.randomUUID())
                .scopeType(ScopeType.INSTITUTION)
                .institutionId(INSTITUTION_ID)
                .status(AssignmentStatus.ACTIVE)
                .build();
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, TARGET_USER_ID, ScopeType.INSTITUTION, INSTITUTION_ID, null, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(previousAssignment));

        when(userRoleAssignmentRepository.save(any(UserRoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        // Keycloak link exists for session revocation (WF-27)
        UserIdentityLink link = UserIdentityLink.builder()
                .identityRealm("test-realm")
                .identitySubject("kc-sub-123")
                .build();
        when(userIdentityLinkRepository.findFirstByIdTenantIdAndUserAccountIdAndStatus(
                TENANT_ID, TARGET_USER_ID, IdentityLinkStatus.ACTIVE))
                .thenReturn(Optional.of(link));

        ResponseEntity<UserRoleAssignmentResponse> response = controller.assignRole(
                TENANT_ID, TARGET_USER_ID, request, auth
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().roleCode()).isEqualTo("TEACHER");
        assertThat(response.getBody().scopeType()).isEqualTo(ScopeType.INSTITUTION);
        assertThat(response.getBody().status()).isEqualTo(AssignmentStatus.ACTIVE);

        // Verify previous assignment ended under Rule 10
        assertThat(previousAssignment.getStatus()).isEqualTo(AssignmentStatus.ENDED);
        assertThat(previousAssignment.getEffectiveTo()).isNotNull();

        // Verify session revocation called under WF-27
        verify(keycloakAdminClient).logoutUser("test-realm", "kc-sub-123");

        // Verify permission check
        verify(permissionEvaluatorService).can(
                eq(new RbacPrincipal(TENANT_ID, ADMIN_USER_ID)),
                eq("ROLE_ASSIGN"),
                eq(RbacScope.institution(INSTITUTION_ID)),
                any(RbacTarget.class)
        );
    }

    @Test
    @DisplayName("ACT-ROLE_ASSIGN-DENY: Unauthorized role is rejected with 403")
    void actRoleAssign_deny() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        AssignRoleRequest request = new AssignRoleRequest(
                ROLE_DEF_ID, null, ScopeType.INSTITUTION, INSTITUTION_ID, null, null, null
        );

        when(permissionEvaluatorService.can(any(), eq("ROLE_ASSIGN"), any(), any()))
                .thenReturn(PermissionDecision.deny("DENIED", "The active role does not hold action ROLE_ASSIGN."));
        doThrow(new UnauthorizedException("The active role does not hold action ROLE_ASSIGN_NON_SENSITIVE."))
                .when(permissionEvaluatorService)
                .authorize(any(), eq("ROLE_ASSIGN_NON_SENSITIVE"), any(), any());

        assertThatThrownBy(() -> controller.assignRole(TENANT_ID, TARGET_USER_ID, request, auth))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-ROLE_ASSIGN-SCOPE: Cross-tenant path is rejected with 403")
    void actRoleAssign_scope() {
        UUID foreignTenantId = UUID.randomUUID();
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        AssignRoleRequest request = new AssignRoleRequest(
                ROLE_DEF_ID, null, ScopeType.TENANT, null, null, null, null
        );

        assertThatThrownBy(() -> controller.assignRole(foreignTenantId, TARGET_USER_ID, request, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("ACT-ROLE_ASSIGN-GUARD: Self-role assignment is rejected with 403 (Addendum Rule 11)")
    void actRoleAssign_guard_selfAssignmentForbidden() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        // Admin attempts to assign role to THEMSELVES:
        AssignRoleRequest request = new AssignRoleRequest(
                ROLE_DEF_ID, null, ScopeType.TENANT, null, null, null, null
        );

        assertThatThrownBy(() -> controller.assignRole(TENANT_ID, ADMIN_USER_ID, request, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("separation of duty");
    }

    @Test
    @DisplayName("ACT-ROLE_ASSIGN-GUARD: Target user not found throws 404")
    void actRoleAssign_guard_targetUserNotFound() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        AssignRoleRequest request = new AssignRoleRequest(
                ROLE_DEF_ID, null, ScopeType.TENANT, null, null, null, null
        );

        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, TARGET_USER_ID)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.assignRole(TENANT_ID, TARGET_USER_ID, request, auth))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("does not exist in tenant");
    }

    @Test
    @DisplayName("ACT-ROLE_ASSIGN-GUARD: endRoleAssignment sets status to ENDED and logs out user (WF-27)")
    void actRoleAssign_guard_endAssignment() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        UserRoleAssignment activeAssignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, ASSIGNMENT_ID))
                .userAccountId(TARGET_USER_ID)
                .roleDefinitionId(ROLE_DEF_ID)
                .scopeType(ScopeType.TENANT)
                .status(AssignmentStatus.ACTIVE)
                .build();

        when(userRoleAssignmentRepository.findByIdTenantIdAndIdUserRoleAssignmentId(TENANT_ID, ASSIGNMENT_ID))
                .thenReturn(Optional.of(activeAssignment));
        when(userRoleAssignmentRepository.save(any(UserRoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleDefinition roleDef = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID))
                .roleCode("ACCOUNTANT")
                .roleName("Accountant")
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID)))
                .thenReturn(Optional.of(roleDef));

        ResponseEntity<UserRoleAssignmentResponse> response = controller.endRoleAssignment(
                TENANT_ID, ASSIGNMENT_ID, auth
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(AssignmentStatus.ENDED);
        assertThat(activeAssignment.getStatus()).isEqualTo(AssignmentStatus.ENDED);
        assertThat(activeAssignment.getEffectiveTo()).isNotNull();

        verify(permissionEvaluatorService).can(
                eq(new RbacPrincipal(TENANT_ID, ADMIN_USER_ID)),
                eq("ROLE_ASSIGN"),
                eq(RbacScope.tenant()),
                any(RbacTarget.class)
        );
    }

    @Test
    @DisplayName("ACT-ROLE_ASSIGN-GUARD: Self-end assignment attempt is rejected with 403 (Addendum Rule 11)")
    void actRoleAssign_guard_selfEndForbidden() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        UserRoleAssignment adminAssignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, ASSIGNMENT_ID))
                .userAccountId(ADMIN_USER_ID) // Assignment belongs to caller
                .roleDefinitionId(ROLE_DEF_ID)
                .scopeType(ScopeType.TENANT)
                .status(AssignmentStatus.ACTIVE)
                .build();

        when(userRoleAssignmentRepository.findByIdTenantIdAndIdUserRoleAssignmentId(TENANT_ID, ASSIGNMENT_ID))
                .thenReturn(Optional.of(adminAssignment));

        assertThatThrownBy(() -> controller.endRoleAssignment(TENANT_ID, ASSIGNMENT_ID, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("separation of duty");
    }

    @Test
    @DisplayName("ACT-ROLE_ASSIGN_NON_SENSITIVE-ALLOW: IT Admin assigns non-sensitive role (OFFICE_STAFF) in institution scope")
    void actRoleAssignNonSensitive_allow() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        AssignRoleRequest request = new AssignRoleRequest(
                ROLE_DEF_ID, null, ScopeType.INSTITUTION, INSTITUTION_ID, null, null, null
        );

        when(permissionEvaluatorService.can(any(), eq("ROLE_ASSIGN"), any(), any()))
                .thenReturn(PermissionDecision.deny("DENIED", "No ROLE_ASSIGN"));

        UserAccount targetAccount = mock(UserAccount.class);
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, TARGET_USER_ID)))
                .thenReturn(Optional.of(targetAccount));

        RoleDefinition nonSensitiveRole = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID))
                .roleCode("OFFICE_STAFF")
                .roleName("Office Staff")
                .roleOrigin(RoleOrigin.STANDARD)
                .roleLevel(RoleLevel.COLLEGE)
                .status(RoleDefinitionStatus.ACTIVE)
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID)))
                .thenReturn(Optional.of(nonSensitiveRole));

        when(userRoleAssignmentRepository.save(any(UserRoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<UserRoleAssignmentResponse> response = controller.assignRole(
                TENANT_ID, TARGET_USER_ID, request, auth
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().roleCode()).isEqualTo("OFFICE_STAFF");

        verify(permissionEvaluatorService).authorize(
                eq(new RbacPrincipal(TENANT_ID, ADMIN_USER_ID)),
                eq("ROLE_ASSIGN_NON_SENSITIVE"),
                eq(RbacScope.institution(INSTITUTION_ID)),
                any(RbacTarget.class)
        );
    }

    @Test
    @DisplayName("ACT-ROLE_ASSIGN_NON_SENSITIVE-DENY: User with neither ROLE_ASSIGN nor ROLE_ASSIGN_NON_SENSITIVE is denied 403")
    void actRoleAssignNonSensitive_deny() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        AssignRoleRequest request = new AssignRoleRequest(
                ROLE_DEF_ID, null, ScopeType.INSTITUTION, INSTITUTION_ID, null, null, null
        );

        when(permissionEvaluatorService.can(any(), eq("ROLE_ASSIGN"), any(), any()))
                .thenReturn(PermissionDecision.deny("DENIED", "No ROLE_ASSIGN"));
        doThrow(new UnauthorizedException("The active role does not hold action ROLE_ASSIGN_NON_SENSITIVE."))
                .when(permissionEvaluatorService)
                .authorize(any(), eq("ROLE_ASSIGN_NON_SENSITIVE"), any(), any());

        assertThatThrownBy(() -> controller.assignRole(TENANT_ID, TARGET_USER_ID, request, auth))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-ROLE_ASSIGN_NON_SENSITIVE-SCOPE: Non-sensitive assigner attempting TENANT scope is refused (Rule MX-04)")
    void actRoleAssignNonSensitive_scope() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        AssignRoleRequest request = new AssignRoleRequest(
                ROLE_DEF_ID, null, ScopeType.TENANT, null, null, null, null
        );

        when(permissionEvaluatorService.can(any(), eq("ROLE_ASSIGN"), any(), any()))
                .thenReturn(PermissionDecision.deny("DENIED", "No ROLE_ASSIGN"));

        UserAccount targetAccount = mock(UserAccount.class);
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, TARGET_USER_ID)))
                .thenReturn(Optional.of(targetAccount));

        RoleDefinition nonSensitiveRole = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID))
                .roleCode("OFFICE_STAFF")
                .roleName("Office Staff")
                .roleOrigin(RoleOrigin.STANDARD)
                .roleLevel(RoleLevel.COLLEGE)
                .status(RoleDefinitionStatus.ACTIVE)
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID)))
                .thenReturn(Optional.of(nonSensitiveRole));

        assertThatThrownBy(() -> controller.assignRole(TENANT_ID, TARGET_USER_ID, request, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException bre = (BusinessRuleViolationException) ex;
                    assertThat(bre.getErrorCode()).isEqualTo("MX_04_INSTITUTION_SCOPE_REQUIRED");
                });
    }

    @Test
    @DisplayName("ACT-ROLE_ASSIGN_NON_SENSITIVE-GUARD: Attempting to assign sensitive role (COLLEGE_ACCOUNTANT) is refused with 422 (Rule MX-04)")
    void actRoleAssignNonSensitive_guard_sensitiveRoleProhibited() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        AssignRoleRequest request = new AssignRoleRequest(
                ROLE_DEF_ID, null, ScopeType.INSTITUTION, INSTITUTION_ID, null, null, null
        );

        when(permissionEvaluatorService.can(any(), eq("ROLE_ASSIGN"), any(), any()))
                .thenReturn(PermissionDecision.deny("DENIED", "No ROLE_ASSIGN"));

        UserAccount targetAccount = mock(UserAccount.class);
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, TARGET_USER_ID)))
                .thenReturn(Optional.of(targetAccount));

        RoleDefinition sensitiveRole = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID))
                .roleCode("COLLEGE_ACCOUNTANT")
                .roleName("College Accountant")
                .roleOrigin(RoleOrigin.STANDARD)
                .roleLevel(RoleLevel.COLLEGE)
                .sensitiveRole(true)
                .status(RoleDefinitionStatus.ACTIVE)
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID)))
                .thenReturn(Optional.of(sensitiveRole));

        assertThatThrownBy(() -> controller.assignRole(TENANT_ID, TARGET_USER_ID, request, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException bre = (BusinessRuleViolationException) ex;
                    assertThat(bre.getErrorCode()).isEqualTo("MX_04_SENSITIVE_ROLE_PROHIBITED");
                });
    }

    @Test
    @DisplayName("ACT-ROLE_ASSIGN_NON_SENSITIVE-GUARD: Attempting to end sensitive role (HEAD_OF_DEPARTMENT) is refused with 422 (Rule MX-04)")
    void actRoleAssignNonSensitive_guard_endSensitiveRoleProhibited() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        UserRoleAssignment activeAssignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, ASSIGNMENT_ID))
                .userAccountId(TARGET_USER_ID)
                .roleDefinitionId(ROLE_DEF_ID)
                .scopeType(ScopeType.INSTITUTION)
                .institutionId(INSTITUTION_ID)
                .status(AssignmentStatus.ACTIVE)
                .build();

        when(userRoleAssignmentRepository.findByIdTenantIdAndIdUserRoleAssignmentId(TENANT_ID, ASSIGNMENT_ID))
                .thenReturn(Optional.of(activeAssignment));

        when(permissionEvaluatorService.can(any(), eq("ROLE_ASSIGN"), any(), any()))
                .thenReturn(PermissionDecision.deny("DENIED", "No ROLE_ASSIGN"));

        RoleDefinition sensitiveRole = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID))
                .roleCode("HEAD_OF_DEPARTMENT")
                .roleName("Head of Department")
                .sensitiveRole(true)
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID)))
                .thenReturn(Optional.of(sensitiveRole));

        assertThatThrownBy(() -> controller.endRoleAssignment(TENANT_ID, ASSIGNMENT_ID, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException bre = (BusinessRuleViolationException) ex;
                    assertThat(bre.getErrorCode()).isEqualTo("MX_04_SENSITIVE_ROLE_PROHIBITED");
                });
    }

    @Test
    @DisplayName("H11: Custom role with benign name but sensitive permissions is detected via roleDefinition.isSensitiveRole()")
    void actRoleAssignNonSensitive_guard_customBenignNameSensitiveRoleProhibited() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID);
        AssignRoleRequest request = new AssignRoleRequest(
                ROLE_DEF_ID, null, ScopeType.INSTITUTION, INSTITUTION_ID, null, null, null
        );

        when(permissionEvaluatorService.can(any(), eq("ROLE_ASSIGN"), any(), any()))
                .thenReturn(PermissionDecision.deny("DENIED", "No ROLE_ASSIGN"));

        UserAccount targetAccount = mock(UserAccount.class);
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, TARGET_USER_ID)))
                .thenReturn(Optional.of(targetAccount));

        RoleDefinition customSensitiveRole = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID))
                .roleCode("OFFICE_HELPER")
                .roleName("Office Helper")
                .roleOrigin(RoleOrigin.CUSTOM)
                .roleLevel(RoleLevel.COLLEGE)
                .sensitiveRole(true)
                .status(RoleDefinitionStatus.ACTIVE)
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID)))
                .thenReturn(Optional.of(customSensitiveRole));

        assertThatThrownBy(() -> controller.assignRole(TENANT_ID, TARGET_USER_ID, request, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException bre = (BusinessRuleViolationException) ex;
                    assertThat(bre.getErrorCode()).isEqualTo("MX_04_SENSITIVE_ROLE_PROHIBITED");
                });
    }
    @Test
    @DisplayName("C2-2: Department role grants are authorized against the exact institution and department scope")
    void departmentRoleGrantUsesExactRequestedScope() {
        UUID departmentId = UUID.randomUUID();
        AssignRoleRequest request = new AssignRoleRequest(
                ROLE_DEF_ID, null, ScopeType.DEPARTMENT, INSTITUTION_ID, departmentId, null, null);

        UserAccount targetAccount = mock(UserAccount.class);
        when(targetAccount.getUserType()).thenReturn(UserType.STAFF);
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, TARGET_USER_ID)))
                .thenReturn(Optional.of(targetAccount));

        RoleDefinition roleDefinition = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID))
                .roleCode("OFFICE_STAFF")
                .roleName("Office Staff")
                .roleOrigin(RoleOrigin.STANDARD)
                .roleLevel(RoleLevel.COLLEGE)
                .status(RoleDefinitionStatus.ACTIVE)
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEF_ID)))
                .thenReturn(Optional.of(roleDefinition));
        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                TENANT_ID, TARGET_USER_ID, ScopeType.DEPARTMENT, INSTITUTION_ID, departmentId, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());
        when(userRoleAssignmentRepository.save(any(UserRoleAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<UserRoleAssignmentResponse> response = controller.assignRole(
                TENANT_ID, TARGET_USER_ID, request, authenticatedTenantUser(TENANT_ID, ADMIN_USER_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().scopeType()).isEqualTo(ScopeType.DEPARTMENT);
        verify(permissionEvaluatorService).can(
                eq(new RbacPrincipal(TENANT_ID, ADMIN_USER_ID)),
                eq("ROLE_ASSIGN"),
                eq(RbacScope.department(INSTITUTION_ID, departmentId)),
                any(RbacTarget.class));
    }

}
