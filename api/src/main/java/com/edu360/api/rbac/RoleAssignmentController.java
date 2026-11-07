package com.edu360.api.rbac;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.dto.AssignRoleRequest;
import com.edu360.api.rbac.dto.RoleDefinitionResponse;
import com.edu360.api.rbac.dto.UserRoleAssignmentResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller exposing administrative role assignment and catalog endpoints:
 * - ROLE_ASSIGN (Sprint 1 Story #14 / WF-27, MX-04)
 *
 * Mapped to both /v1/tenants/{tenantId}/... (target OpenAPI specification)
 * and /api/tenants/{tenantId}/... (current monolithic application convention).
 */
@RestController
@RequestMapping({"/v1/tenants/{tenantId}", "/api/tenants/{tenantId}"})
@RequiredArgsConstructor
@Tag(name = "Users and roles", description = "Role assignments and catalogue management (ROLE_ASSIGN)")
public class RoleAssignmentController {

    private final RoleAssignmentService roleAssignmentService;

    @GetMapping("/roles")
    @Operation(summary = "List roles available for assignment in this tenant")
    public ResponseEntity<List<RoleDefinitionResponse>> listRoles(
            @PathVariable UUID tenantId,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(roleAssignmentService.listRoles(principal));
    }

    @GetMapping("/users/{userId}/role-assignments")
    @Operation(summary = "List active and historical role assignments of a user")
    public ResponseEntity<List<UserRoleAssignmentResponse>> listUserRoleAssignments(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(roleAssignmentService.listUserRoleAssignments(principal, userId));
    }

    @PostMapping("/users/{userId}/role-assignments")
    @Operation(summary = "Assign a role to a user (ROLE_ASSIGN)")
    public ResponseEntity<UserRoleAssignmentResponse> assignRole(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRoleRequest request,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        UserRoleAssignmentResponse response = roleAssignmentService.assignRole(principal, userId, request);
        return ResponseEntity.created(URI.create("/api/tenants/" + tenantId + "/users/" + userId + "/role-assignments/" + response.userRoleAssignmentId()))
                .body(response);
    }

    @PostMapping("/role-assignments/{assignmentId}/end")
    @Operation(summary = "End an assignment; user is logged out at once (ROLE_ASSIGN / WF-27)")
    public ResponseEntity<UserRoleAssignmentResponse> endRoleAssignment(
            @PathVariable UUID tenantId,
            @PathVariable UUID assignmentId,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(roleAssignmentService.endRoleAssignment(principal, assignmentId));
    }

    private RbacPrincipal extractTenantPrincipal(UUID tenantId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Role operations require authenticated tenant principal.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in path does not match authenticated tenant.");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}
