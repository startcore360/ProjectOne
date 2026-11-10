package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.dto.CreateCustomRoleRequest;
import com.edu360.api.rbac.dto.RoleDetailResponse;

class RoleCustomControllerTest {

    private RoleDefinitionRepository roleDefinitionRepository;
    private RoleActionRepository roleActionRepository;
    private ActionCatalogRepository actionCatalogRepository;
    private PermissionEvaluatorService permissionEvaluatorService;
    private AuditLogService auditLogService;
    private RoleCustomService roleCustomService;
    private RoleCustomController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        roleDefinitionRepository = mock(RoleDefinitionRepository.class);
        roleActionRepository = mock(RoleActionRepository.class);
        actionCatalogRepository = mock(ActionCatalogRepository.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        auditLogService = mock(AuditLogService.class);

        roleCustomService = new RoleCustomService(
                roleDefinitionRepository,
                roleActionRepository,
                actionCatalogRepository,
                permissionEvaluatorService,
                auditLogService
        );

        controller = new RoleCustomController(roleCustomService);
    }

    private Authentication authFor(UUID tenantId, UUID userId) {
        TenantPrincipal principal = new TenantPrincipal(tenantId, userId, UserType.STAFF, "admin@university.test", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void ALLOW_universityAdminCreatesCustomRoleWithActions() {
        when(roleDefinitionRepository.existsByIdTenantIdAndRoleCode(TENANT_ID, "CUSTOM_EXAM_CELL")).thenReturn(false);
        when(actionCatalogRepository.findById("USER_VIEW")).thenReturn(Optional.of(new ActionCatalog()));
        when(roleDefinitionRepository.save(any(RoleDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCustomRoleRequest request = new CreateCustomRoleRequest(
                "CUSTOM_EXAM_CELL",
                "Custom Exam Cell Coordinator",
                RoleLevel.COLLEGE,
                CustomScope.INSTITUTION,
                List.of("USER_VIEW")
        );

        ResponseEntity<RoleDetailResponse> response = controller.createCustomRole(
                TENANT_ID, request, authFor(TENANT_ID, USER_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().roleCode()).isEqualTo("CUSTOM_EXAM_CELL");
        assertThat(response.getBody().roleOrigin()).isEqualTo(RoleOrigin.CUSTOM);
        assertThat(response.getBody().actionCodes()).contains("USER_VIEW");
        verify(auditLogService).record(eq(AuditEventType.CUSTOM_ROLE_CREATED), any(), any(), any(), any());
    }

    @Test
    void DENY_duplicateRoleCodeThrowsConflict() {
        when(roleDefinitionRepository.existsByIdTenantIdAndRoleCode(TENANT_ID, "CUSTOM_EXAM_CELL")).thenReturn(true);

        CreateCustomRoleRequest request = new CreateCustomRoleRequest(
                "CUSTOM_EXAM_CELL",
                "Custom Exam Cell Coordinator",
                RoleLevel.COLLEGE,
                CustomScope.INSTITUTION,
                List.of()
        );

        assertThatThrownBy(() -> controller.createCustomRole(TENANT_ID, request, authFor(TENANT_ID, USER_ID)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("CUSTOM_EXAM_CELL");
    }

    @Test
    void ALLOW_retireCustomRoleSetsStatusRetired() {
        UUID roleId = UUID.randomUUID();
        RoleDefinition role = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, roleId))
                .roleCode("CUSTOM_LAB_ASSISTANT")
                .roleName("Custom Lab Assistant")
                .roleOrigin(RoleOrigin.CUSTOM)
                .roleLevel(RoleLevel.SCHOOL)
                .status(RoleDefinitionStatus.ACTIVE)
                .build();

        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, roleId))).thenReturn(Optional.of(role));
        when(roleDefinitionRepository.save(any(RoleDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleActionRepository.findByIdTenantIdAndRoleDefinitionId(TENANT_ID, roleId)).thenReturn(List.of());

        ResponseEntity<RoleDetailResponse> response = controller.retireCustomRole(
                TENANT_ID, roleId, authFor(TENANT_ID, USER_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(RoleDefinitionStatus.RETIRED);
        verify(auditLogService).record(eq(AuditEventType.CUSTOM_ROLE_RETIRED), any(), any(), any(), any());
    }

    @Test
    void ALLOW_getRoleDetails() {
        UUID roleId = UUID.randomUUID();
        RoleDefinition role = RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT_ID, roleId))
                .roleCode("CUSTOM_CAMPUS_INCHARGE")
                .roleName("Custom Campus Incharge")
                .roleOrigin(RoleOrigin.CUSTOM)
                .roleLevel(RoleLevel.COLLEGE)
                .status(RoleDefinitionStatus.ACTIVE)
                .build();

        when(roleDefinitionRepository.findById(new RoleDefinitionId(TENANT_ID, roleId))).thenReturn(Optional.of(role));

        ResponseEntity<RoleDetailResponse> getRes = controller.getRole(
                TENANT_ID, roleId, authFor(TENANT_ID, USER_ID));
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getRes.getBody().roleCode()).isEqualTo("CUSTOM_CAMPUS_INCHARGE");
    }

    @Test
    void DENY_mismatchedTenantIdReturnsUnauthorized() {
        UUID otherTenantId = UUID.randomUUID();
        assertThatThrownBy(() -> controller.getRole(otherTenantId, UUID.randomUUID(), authFor(TENANT_ID, USER_ID)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void DENY_grantingLockedActionInCustomRoleForbidden() {
        CreateCustomRoleRequest request = new CreateCustomRoleRequest(
                "CUSTOM_PAYROLL_MASTER",
                "Custom Payroll",
                RoleLevel.COLLEGE,
                CustomScope.INSTITUTION,
                List.of("PAYROLL_APPROVE")
        );

        assertThatThrownBy(() -> controller.createCustomRole(TENANT_ID, request, authFor(TENANT_ID, USER_ID)))
                .isInstanceOf(com.edu360.api.exception.BusinessRuleViolationException.class)
                .hasMessageContaining("locked action");
    }
}
