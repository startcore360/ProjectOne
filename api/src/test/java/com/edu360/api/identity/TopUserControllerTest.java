package com.edu360.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.dto.TopUserCreateRequest;
import com.edu360.api.identity.dto.TopUserResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.RoleAssignmentService;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.dto.AssignRoleRequest;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

/**
 * Action & Guard tests for Story #23 (TENANT_TOP_USERS_CREATE / Action #268):
 * - ACT-TENANT_TOP_USERS_CREATE-ALLOW (Vice Chancellor / UserType.STAFF)
 * - ACT-TENANT_TOP_USERS_CREATE-ALLOW (Governing Body Approver / UserType.GOVERNANCE / Decision C9)
 * - ACT-TENANT_TOP_USERS_CREATE-DENY
 * - ACT-TENANT_TOP_USERS_CREATE-SCOPE
 * - ACT-TENANT_TOP_USERS_CREATE-GUARD (Addendum Rule 11: self-assignment prohibited)
 * - ACT-TENANT_TOP_USERS_CREATE-GUARD (Duplicate email check)
 * - ACT-TENANT_TOP_USERS_CREATE-GUARD (Invalid top role rejection)
 */
class TopUserControllerTest {

    private UserAccountRepository userAccountRepository;
    private UserIdentityLinkRepository userIdentityLinkRepository;
    private TenantRepository tenantRepository;
    private PermissionEvaluatorService permissionEvaluatorService;
    private KeycloakAdminClient keycloakAdminClient;
    private RoleAssignmentService roleAssignmentService;
    private AuditLogService auditLogService;

    private TopUserService topUserService;
    private TopUserController controller;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID foreignTenantId = UUID.randomUUID();

    private Authentication auth;

    @BeforeEach
    void setUp() {
        userAccountRepository = mock(UserAccountRepository.class);
        userIdentityLinkRepository = mock(UserIdentityLinkRepository.class);
        tenantRepository = mock(TenantRepository.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        keycloakAdminClient = mock(KeycloakAdminClient.class);
        roleAssignmentService = mock(RoleAssignmentService.class);
        auditLogService = mock(AuditLogService.class);

        topUserService = new TopUserService(
                userAccountRepository,
                userIdentityLinkRepository,
                tenantRepository,
                permissionEvaluatorService,
                keycloakAdminClient,
                roleAssignmentService,
                auditLogService
        );

        controller = new TopUserController(topUserService);

        TenantPrincipal principal = new TenantPrincipal(tenantId, callerId, UserType.STAFF, "Admin User", false);
        auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        UserAccount callerAccount = UserAccount.builder()
                .id(new UserAccountId(tenantId, callerId))
                .email("admin@northbridge.edu.in")
                .displayName("Admin User")
                .userType(UserType.STAFF)
                .status(UserAccountStatus.ACTIVE)
                .build();
        when(userAccountRepository.findById(new UserAccountId(tenantId, callerId))).thenReturn(Optional.of(callerAccount));

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(
                Tenant.builder().keycloakRealm("northbridge-realm").build()
        ));
    }

    @Test
    @DisplayName("ALLOW: University Admin creates Vice Chancellor account (UserType.STAFF, TENANT scope)")
    void testCreateTopUser_ViceChancellor_Success() {
        TopUserCreateRequest request = new TopUserCreateRequest(
                "VICE_CHANCELLOR",
                "Prof. Suresh Chandra",
                "vc@northbridge.edu.in",
                "+919876543210"
        );

        when(userAccountRepository.existsByIdTenantIdAndEmail(tenantId, "vc@northbridge.edu.in")).thenReturn(false);
        when(keycloakAdminClient.provisionUser(any(), any(), any(), any(), eq(true))).thenReturn("kc-vc-uuid");
        when(userAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<TopUserResponse> response = controller.createTopUser(tenantId, request, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TopUserResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.displayName()).isEqualTo("Prof. Suresh Chandra");
        assertThat(body.email()).isEqualTo("vc@northbridge.edu.in");
        assertThat(body.roleCode()).isEqualTo("VICE_CHANCELLOR");
        assertThat(body.userType()).isEqualTo(UserType.STAFF); // Decision C9
        assertThat(body.twoStepLoginRequired()).isTrue(); // Rule F-12
        assertThat(body.temporaryPassword()).isNotNull();

        // Verify role assignment in TENANT scope
        ArgumentCaptor<AssignRoleRequest> roleCaptor = ArgumentCaptor.forClass(AssignRoleRequest.class);
        verify(roleAssignmentService).assignRole(any(), eq(body.userAccountId()), roleCaptor.capture());
        assertThat(roleCaptor.getValue().roleCode()).isEqualTo("VICE_CHANCELLOR");
        assertThat(roleCaptor.getValue().scopeType()).isEqualTo(ScopeType.TENANT);

        // Verify audit log
        verify(auditLogService).record(eq(AuditEventType.TOP_USER_CREATED), any(), eq(callerId.toString()), any(), any());
    }

    @Test
    @DisplayName("ALLOW: Tenant Admin creates Governing Body Approver (UserType.GOVERNANCE / Decision C9)")
    void testCreateTopUser_GoverningBodyApprover_Success() {
        TopUserCreateRequest request = new TopUserCreateRequest(
                "GOVERNING_BODY_APPROVER",
                "Hon. Justice K. Raman",
                "gba@northbridge.edu.in",
                null
        );

        when(userAccountRepository.existsByIdTenantIdAndEmail(tenantId, "gba@northbridge.edu.in")).thenReturn(false);
        when(keycloakAdminClient.provisionUser(any(), any(), any(), any(), eq(true))).thenReturn("kc-gba-uuid");
        when(userAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<TopUserResponse> response = controller.createTopUser(tenantId, request, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TopUserResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.roleCode()).isEqualTo("GOVERNING_BODY_APPROVER");
        assertThat(body.userType()).isEqualTo(UserType.GOVERNANCE); // Decision C9
        assertThat(body.twoStepLoginRequired()).isTrue(); // Rule F-12
    }

    @Test
    @DisplayName("DENY: Caller without TENANT_TOP_USERS_CREATE permission is rejected with 403")
    void testCreateTopUser_Unauthorized_Denied() {
        doThrow(new AccessDeniedException("Forbidden: missing TENANT_TOP_USERS_CREATE"))
                .when(permissionEvaluatorService).authorize(
                        any(),
                        eq("TENANT_TOP_USERS_CREATE"),
                        any(),
                        eq(RbacTarget.NONE)
                );

        TopUserCreateRequest request = new TopUserCreateRequest(
                "VICE_CHANCELLOR",
                "Prof. Chandra",
                "vc@test.edu",
                null
        );

        assertThatThrownBy(() -> controller.createTopUser(tenantId, request, auth))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("TENANT_TOP_USERS_CREATE");

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("SCOPE: Mismatching tenantId in path is rejected")
    void testCreateTopUser_ForeignTenant_Rejected() {
        TopUserCreateRequest request = new TopUserCreateRequest(
                "MANAGEMENT",
                "Trustee Board",
                "mgmt@test.edu",
                null
        );

        assertThatThrownBy(() -> controller.createTopUser(foreignTenantId, request, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("tenantId in the path does not match");
    }

    @Test
    @DisplayName("GUARD (Addendum Rule 11): Caller attempting self-assignment to top role is rejected with 422")
    void testCreateTopUser_SelfAssignment_Prohibited() {
        // Request uses caller's own email
        TopUserCreateRequest request = new TopUserCreateRequest(
                "MANAGEMENT",
                "Admin Self",
                "admin@northbridge.edu.in",
                null
        );

        assertThatThrownBy(() -> controller.createTopUser(tenantId, request, auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Addendum rule 11");

        verify(userAccountRepository, never()).save(any());
        verify(roleAssignmentService, never()).assignRole(any(), any(), any());
    }

    @Test
    @DisplayName("GUARD: Duplicate email in tenant is rejected with DuplicateResourceException")
    void testCreateTopUser_DuplicateEmail_Rejected() {
        TopUserCreateRequest request = new TopUserCreateRequest(
                "DATA_PROTECTION_CONTACT",
                "DPO Officer",
                "existing.dpo@northbridge.edu.in",
                null
        );

        when(userAccountRepository.existsByIdTenantIdAndEmail(tenantId, "existing.dpo@northbridge.edu.in")).thenReturn(true);

        assertThatThrownBy(() -> controller.createTopUser(tenantId, request, auth))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists in this tenant");

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("GUARD: Invalid role code rejected with BusinessRuleViolationException")
    void testCreateTopUser_InvalidRoleCode_Rejected() {
        TopUserCreateRequest request = new TopUserCreateRequest(
                "REGULAR_TEACHER",
                "Someone",
                "teacher@northbridge.edu.in",
                null
        );

        assertThatThrownBy(() -> controller.createTopUser(tenantId, request, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not an allowed top governance role");
    }

    @Test
    @DisplayName("D10 / BS-21: Keycloak provisioning failure propagates KeycloakProvisioningException without saving database rows")
    void testCreateTopUser_KeycloakFailure_PropagatesException() {
        TopUserCreateRequest request = new TopUserCreateRequest(
                "VICE_CHANCELLOR",
                "Prof. Keycloak Fail",
                "fail.vc@northbridge.edu.in",
                "+919876543210"
        );

        when(userAccountRepository.existsByIdTenantIdAndEmail(tenantId, "fail.vc@northbridge.edu.in")).thenReturn(false);
        when(keycloakAdminClient.provisionUser(any(), any(), any(), any(), eq(true)))
                .thenThrow(new com.edu360.api.config.keycloak.KeycloakProvisioningException("Keycloak service connection timeout"));

        assertThatThrownBy(() -> controller.createTopUser(tenantId, request, auth))
                .isInstanceOf(com.edu360.api.config.keycloak.KeycloakProvisioningException.class)
                .hasMessageContaining("Keycloak service connection timeout");

        verify(userAccountRepository, never()).save(any());
        verify(roleAssignmentService, never()).assignRole(any(), any(), any());
    }
}
