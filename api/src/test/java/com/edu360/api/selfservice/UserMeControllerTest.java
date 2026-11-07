package com.edu360.api.selfservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.institution.DepartmentRepository;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.RoleOrigin;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentId;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.selfservice.dto.SwitchActiveRoleRequest;
import com.edu360.api.selfservice.dto.SwitchActiveRoleResponse;
import com.edu360.api.selfservice.dto.UserMeResponse;
import com.edu360.api.selfservice.dto.UserPermissionsResponse;

/**
 * Action & Guard tests for ROLE_SWITCH (Sprint 1 Story #6 / Rule HI-15):
 * - ACT-ROLE_SWITCH-ALLOW
 * - ACT-ROLE_SWITCH-DENY
 * - ACT-ROLE_SWITCH-SCOPE
 * - ACT-ROLE_SWITCH-GUARD (inactive / expired assignment)
 */
class UserMeControllerTest {

    private UserAccountRepository userAccountRepository;
    private UserRoleAssignmentRepository userRoleAssignmentRepository;
    private RoleDefinitionRepository roleDefinitionRepository;
    private InstitutionRepository institutionRepository;
    private DepartmentRepository departmentRepository;
    private AuditLogService auditLogService;
    private UserMeService userMeService;
    private UserMeController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ASSIGNMENT_1 = UUID.randomUUID();
    private static final UUID ASSIGNMENT_2 = UUID.randomUUID();
    private static final UUID ROLE_DEF_1 = UUID.randomUUID();
    private static final UUID ROLE_DEF_2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userAccountRepository = mock(UserAccountRepository.class);
        userRoleAssignmentRepository = mock(UserRoleAssignmentRepository.class);
        roleDefinitionRepository = mock(RoleDefinitionRepository.class);
        institutionRepository = mock(InstitutionRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        auditLogService = mock(AuditLogService.class);

        userMeService = new UserMeService(
                userAccountRepository,
                userRoleAssignmentRepository,
                roleDefinitionRepository,
                institutionRepository,
                departmentRepository,
                auditLogService
        );

        controller = new UserMeController(userMeService);
    }

    private Authentication authenticatedTenantUser(UUID tenantId, UUID userId) {
        TenantPrincipal principal = new TenantPrincipal(
                tenantId,
                userId,
                UserType.STAFF,
                "Dr. Vikram Sarabhai",
                false
        );
        return new UsernamePasswordAuthenticationToken(principal, "n/a", Collections.emptyList());
    }

    @Test
    @DisplayName("ACT-ROLE_SWITCH-ALLOW: Authenticated user switches active role to valid second assignment")
    void actRoleSwitch_allow() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        SwitchActiveRoleRequest request = new SwitchActiveRoleRequest(ASSIGNMENT_2);

        // Assignment 2 belongs to USER_ID and is ACTIVE
        UserRoleAssignment assignment2 = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, ASSIGNMENT_2))
                .userAccountId(USER_ID)
                .roleDefinitionId(ROLE_DEF_2)
                .scopeType(ScopeType.INSTITUTION)
                .institutionId(UUID.randomUUID())
                .status(AssignmentStatus.ACTIVE)
                .build();
        when(userRoleAssignmentRepository.findByIdTenantIdAndIdUserRoleAssignmentId(TENANT_ID, ASSIGNMENT_2))
                .thenReturn(Optional.of(assignment2));

        RoleDefinition roleDef2 = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEF_2))
                .roleCode("HEAD_OF_DEPARTMENT")
                .roleName("Head of Department")
                .roleOrigin(RoleOrigin.STANDARD)
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEF_2)))
                .thenReturn(Optional.of(roleDef2));

        ResponseEntity<SwitchActiveRoleResponse> response = controller.switchActiveRole(TENANT_ID, request, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().roleCode()).isEqualTo("HEAD_OF_DEPARTMENT");
        assertThat(response.getBody().assignmentId()).isEqualTo(ASSIGNMENT_2);

        // Verify audit log
        verify(auditLogService).record(
                eq(AuditEventType.ROLE_SWITCHED),
                any(),
                eq(USER_ID.toString()),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("ACT-ROLE_SWITCH-DENY: Unauthenticated caller is rejected with 401/403")
    void actRoleSwitch_deny() {
        SwitchActiveRoleRequest request = new SwitchActiveRoleRequest(ASSIGNMENT_2);

        assertThatThrownBy(() -> controller.switchActiveRole(TENANT_ID, request, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("ACT-ROLE_SWITCH-SCOPE: Caller attempting to switch to foreign tenant path is rejected with 403")
    void actRoleSwitch_scope_foreignTenantPath() {
        UUID foreignTenantId = UUID.randomUUID();
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        SwitchActiveRoleRequest request = new SwitchActiveRoleRequest(ASSIGNMENT_2);

        assertThatThrownBy(() -> controller.switchActiveRole(foreignTenantId, request, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Tenant ID in path does not match");
    }

    @Test
    @DisplayName("ACT-ROLE_SWITCH-SCOPE: Caller attempting to switch to role assignment belonging to another user is refused")
    void actRoleSwitch_scope_foreignUserAssignment() {
        UUID anotherUserId = UUID.randomUUID();
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        SwitchActiveRoleRequest request = new SwitchActiveRoleRequest(ASSIGNMENT_2);

        // Assignment belongs to anotherUserId:
        UserRoleAssignment foreignAssignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, ASSIGNMENT_2))
                .userAccountId(anotherUserId)
                .roleDefinitionId(ROLE_DEF_2)
                .scopeType(ScopeType.INSTITUTION)
                .status(AssignmentStatus.ACTIVE)
                .build();
        when(userRoleAssignmentRepository.findByIdTenantIdAndIdUserRoleAssignmentId(TENANT_ID, ASSIGNMENT_2))
                .thenReturn(Optional.of(foreignAssignment));

        assertThatThrownBy(() -> controller.switchActiveRole(TENANT_ID, request, auth))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("ACT-ROLE_SWITCH-GUARD: Attempting to switch to inactive (ENDED) role assignment is refused with 422")
    void actRoleSwitch_guard_inactiveAssignmentRefused() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        SwitchActiveRoleRequest request = new SwitchActiveRoleRequest(ASSIGNMENT_2);

        UserRoleAssignment endedAssignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, ASSIGNMENT_2))
                .userAccountId(USER_ID)
                .roleDefinitionId(ROLE_DEF_2)
                .scopeType(ScopeType.INSTITUTION)
                .status(AssignmentStatus.ENDED)
                .build();
        when(userRoleAssignmentRepository.findByIdTenantIdAndIdUserRoleAssignmentId(TENANT_ID, ASSIGNMENT_2))
                .thenReturn(Optional.of(endedAssignment));

        assertThatThrownBy(() -> controller.switchActiveRole(TENANT_ID, request, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException bre = (BusinessRuleViolationException) ex;
                    assertThat(bre.getErrorCode()).isEqualTo("INACTIVE_ROLE_ASSIGNMENT");
                });
    }

    @Test
    @DisplayName("ACT-ROLE_SWITCH-GUARD: Attempting to switch to expired role assignment is refused with 422")
    void actRoleSwitch_guard_expiredAssignmentRefused() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);
        SwitchActiveRoleRequest request = new SwitchActiveRoleRequest(ASSIGNMENT_2);

        UserRoleAssignment expiredAssignment = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, ASSIGNMENT_2))
                .userAccountId(USER_ID)
                .roleDefinitionId(ROLE_DEF_2)
                .scopeType(ScopeType.INSTITUTION)
                .status(AssignmentStatus.ACTIVE)
                .effectiveTo(Instant.now().minusSeconds(3600)) // Expired 1 hour ago
                .build();
        when(userRoleAssignmentRepository.findByIdTenantIdAndIdUserRoleAssignmentId(TENANT_ID, ASSIGNMENT_2))
                .thenReturn(Optional.of(expiredAssignment));

        assertThatThrownBy(() -> controller.switchActiveRole(TENANT_ID, request, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException bre = (BusinessRuleViolationException) ex;
                    assertThat(bre.getErrorCode()).isEqualTo("EXPIRED_ROLE_ASSIGNMENT");
                });
    }

    @Test
    @DisplayName("GET /me returns user profile and list of available roles for switching")
    void getMe_returnsProfileAndRoleOptions() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);

        UserAccount user = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ID))
                .email("vikram.sarabhai@northbridge.edu.in")
                .displayName("Dr. Vikram Sarabhai")
                .userType(UserType.STAFF)
                .build();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ID)))
                .thenReturn(Optional.of(user));

        UserRoleAssignment a1 = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, ASSIGNMENT_1))
                .userAccountId(USER_ID)
                .roleDefinitionId(ROLE_DEF_1)
                .scopeType(ScopeType.INSTITUTION)
                .status(AssignmentStatus.ACTIVE)
                .build();
        UserRoleAssignment a2 = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, ASSIGNMENT_2))
                .userAccountId(USER_ID)
                .roleDefinitionId(ROLE_DEF_2)
                .scopeType(ScopeType.DEPARTMENT)
                .status(AssignmentStatus.ACTIVE)
                .build();

        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountId(TENANT_ID, USER_ID))
                .thenReturn(List.of(a1, a2));

        RoleDefinition rd1 = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEF_1))
                .roleCode("PROFESSOR")
                .roleName("Professor")
                .build();
        RoleDefinition rd2 = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEF_2))
                .roleCode("HEAD_OF_DEPARTMENT")
                .roleName("Head of Department")
                .build();

        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEF_1))).thenReturn(Optional.of(rd1));
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEF_2))).thenReturn(Optional.of(rd2));

        ResponseEntity<UserMeResponse> response = controller.getMe(TENANT_ID, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo("vikram.sarabhai@northbridge.edu.in");
        assertThat(response.getBody().roles()).hasSize(2);
        assertThat(response.getBody().roles().get(0).roleCode()).isEqualTo("PROFESSOR");
        assertThat(response.getBody().roles().get(1).roleCode()).isEqualTo("HEAD_OF_DEPARTMENT");
    }

    @Test
    @DisplayName("GET /me/permissions: Returns active role code and scope type")
    void getPermissions_returnsActiveRoleAndScope() {
        Authentication auth = authenticatedTenantUser(TENANT_ID, USER_ID);

        UserAccount user = UserAccount.builder()
                .id(new UserAccountId(TENANT_ID, USER_ID))
                .email("vikram.sarabhai@northbridge.edu.in")
                .displayName("Dr. Vikram Sarabhai")
                .userType(UserType.STAFF)
                .build();
        when(userAccountRepository.findById(new UserAccountId(TENANT_ID, USER_ID)))
                .thenReturn(Optional.of(user));

        UserRoleAssignment a1 = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT_ID, ASSIGNMENT_1))
                .userAccountId(USER_ID)
                .roleDefinitionId(ROLE_DEF_1)
                .scopeType(ScopeType.INSTITUTION)
                .status(AssignmentStatus.ACTIVE)
                .build();

        when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountId(TENANT_ID, USER_ID))
                .thenReturn(List.of(a1));
        when(userRoleAssignmentRepository.findByIdTenantIdAndIdUserRoleAssignmentId(TENANT_ID, ASSIGNMENT_1))
                .thenReturn(Optional.of(a1));

        RoleDefinition rd1 = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, ROLE_DEF_1))
                .roleCode("PROFESSOR")
                .roleName("Professor")
                .build();
        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, ROLE_DEF_1))).thenReturn(Optional.of(rd1));

        ResponseEntity<UserPermissionsResponse> response = controller.getPermissions(TENANT_ID, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().activeRoleCode()).isEqualTo("PROFESSOR");
        assertThat(response.getBody().scopeType()).isEqualTo(ScopeType.INSTITUTION);
    }
}
