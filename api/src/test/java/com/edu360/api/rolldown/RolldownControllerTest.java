package com.edu360.api.rolldown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.identity.UserType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class RolldownControllerTest {

    @Mock
    private RoleRolldownOverrideRepository overrideRepository;
    @Mock
    private RoleRolldownDefaultRepository defaultRepository;
    @Mock
    private PermissionEvaluatorService permissionEvaluatorService;
    @Mock
    private AuditLogService auditLogService;

    private RolldownService service;
    private RolldownController controller;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Authentication auth;

    @BeforeEach
    void setUp() {
        service = new RolldownService(
                overrideRepository,
                defaultRepository,
                permissionEvaluatorService,
                auditLogService
        );
        controller = new RolldownController(service);

        TenantPrincipal principal = new TenantPrincipal(
                tenantId,
                userId,
                UserType.STAFF,
                "Dr. John von Neumann",
                false
        );
        auth = new UsernamePasswordAuthenticationToken(principal, null);
        TenantContext.set(tenantId, IsolationMode.SHARED, null, "ALL", java.util.Set.of(), "MANAGEMENT", ScopeType.TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("ACT-ROLLDOWN_VIEW-ALLOW: Authorized Management/VC views rolldown map")
    void ALLOW_viewRolldownMap() {
        doNothing().when(permissionEvaluatorService).authorize(any(), eq("ROLLDOWN_VIEW"), any(), any());

        RoleRolldownDefault def = new RoleRolldownDefault(1, "VICE_CHANCELLOR", "UNIVERSITY_WIDE", "APPROVE", (short) 1, "REGISTRAR", "Registrar acts for VC");
        when(defaultRepository.findByContextOrderByMissingRoleCodeAscPreferenceAsc("UNIVERSITY_WIDE"))
                .thenReturn(List.of(def));
        when(overrideRepository.findByIdTenantIdAndStatus(tenantId, "ACTIVE"))
                .thenReturn(List.of());

        ResponseEntity<List<RolldownMapEntryDto>> res = controller.getRolldownMap(tenantId, null, null, auth);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).hasSize(1);
        assertThat(res.getBody().get(0).standInRoleCode()).isEqualTo("REGISTRAR");
    }

    @Test
    @DisplayName("ACT-ROLLDOWN_VIEW-DENY: Unauthorized role is rejected")
    void DENY_viewRolldownMap() {
        doThrow(new AccessDeniedException("ACTION_NOT_GRANTED"))
                .when(permissionEvaluatorService).authorize(any(), eq("ROLLDOWN_VIEW"), any(), any());

        assertThatThrownBy(() -> controller.getRolldownMap(tenantId, null, null, auth))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ACT-ROLLDOWN_VIEW-SCOPE: Cross-tenant rolldown map access is forbidden")
    void SCOPE_crossTenantRefused() {
        UUID otherTenant = UUID.randomUUID();
        assertThatThrownBy(() -> controller.getRolldownMap(otherTenant, null, null, auth))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cross-tenant access forbidden");
    }

    @Test
    @DisplayName("ACT-ROLLDOWN_OVERRIDE_SET-ALLOW: Management sets a valid rolldown override to another role")
    void ALLOW_managementSetsOverride() {
        doNothing().when(permissionEvaluatorService).authorize(any(), eq("ROLLDOWN_OVERRIDE_SET"), any(), any());

        when(overrideRepository.save(any(RoleRolldownOverride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRolldownOverrideRequest req = new CreateRolldownOverrideRequest(
                null,
                "VICE_CHANCELLOR",
                "APPROVE",
                null,
                "ASSIGN",
                "REGISTRAR",
                "Temporary assignment during sabbatical leave"
        );

        ResponseEntity<RolldownOverrideDto> res = controller.createOverride(tenantId, "MANAGEMENT", req, auth);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().targetRoleCode()).isEqualTo("REGISTRAR");
    }

    @Test
    @DisplayName("ACT-ROLLDOWN_OVERRIDE_SET-GUARD: Guard RD-G2 blocks withholding an approval action")
    void GUARD_withholdApprovalBlocked() {
        doNothing().when(permissionEvaluatorService).authorize(any(), eq("ROLLDOWN_OVERRIDE_SET"), any(), any());

        CreateRolldownOverrideRequest req = new CreateRolldownOverrideRequest(
                null,
                "VICE_CHANCELLOR",
                "APPROVE",
                null,
                "WITHHOLD",
                null,
                "Attempt to withhold approval"
        );

        assertThatThrownBy(() -> controller.createOverride(tenantId, "MANAGEMENT", req, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("never withheld");
    }

    @Test
    @DisplayName("ACT-ROLLDOWN_OVERRIDE_SET-GUARD: Guard blocks unrollable missing role (e.g. STUDENT_COLLEGE)")
    void GUARD_unrollableMissingRoleBlocked() {
        doNothing().when(permissionEvaluatorService).authorize(any(), eq("ROLLDOWN_OVERRIDE_SET"), any(), any());

        CreateRolldownOverrideRequest req = new CreateRolldownOverrideRequest(
                null,
                "STUDENT_COLLEGE",
                "OTHER",
                null,
                "ASSIGN",
                "OFFICE_STAFF",
                "Invalid roll-down"
        );

        assertThatThrownBy(() -> controller.createOverride(tenantId, "MANAGEMENT", req, auth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("never rolls down");
    }
}
