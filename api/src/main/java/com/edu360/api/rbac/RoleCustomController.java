package com.edu360.api.rbac;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.dto.CreateCustomRoleRequest;
import com.edu360.api.rbac.dto.RoleDetailResponse;
import com.edu360.api.rbac.dto.UpdateCustomRoleRequest;
import com.edu360.api.rbac.dto.UpdateRoleActionSetRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/tenants/{tenantId}/roles", "/api/tenants/{tenantId}/roles"})
@RequiredArgsConstructor
@Tag(name = "Roles & Permissions", description = "Role catalogue and custom role management (ROLE_CUSTOM_MANAGE)")
public class RoleCustomController {

    private final RoleCustomService roleCustomService;

    // GET .../roles (the list) is served by RoleAssignmentController; declaring it here as well
    // makes Spring refuse to start with an ambiguous mapping.

    @GetMapping("/{roleDefinitionId}")
    @Operation(summary = "Get role details with granted actions")
    public ResponseEntity<RoleDetailResponse> getRole(
            @PathVariable UUID tenantId,
            @PathVariable UUID roleDefinitionId,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(roleCustomService.getRole(principal, roleDefinitionId));
    }

    @PostMapping
    @Operation(summary = "Create custom role with assigned actions (ROLE_CUSTOM_MANAGE / Rules 3, 4)")
    public ResponseEntity<RoleDetailResponse> createCustomRole(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateCustomRoleRequest request,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        RoleDetailResponse response = roleCustomService.createCustomRole(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{roleDefinitionId}")
    @Operation(summary = "Update custom role details (ROLE_CUSTOM_MANAGE)")
    public ResponseEntity<RoleDetailResponse> updateCustomRole(
            @PathVariable UUID tenantId,
            @PathVariable UUID roleDefinitionId,
            @Valid @RequestBody UpdateCustomRoleRequest request,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(roleCustomService.updateCustomRole(principal, roleDefinitionId, request));
    }

    @PostMapping("/{roleDefinitionId}/action-set")
    @Operation(summary = "Update action set for custom role (ROLE_CUSTOM_MANAGE)")
    public ResponseEntity<RoleDetailResponse> updateRoleActionSet(
            @PathVariable UUID tenantId,
            @PathVariable UUID roleDefinitionId,
            @Valid @RequestBody UpdateRoleActionSetRequest request,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(roleCustomService.updateRoleActionSet(principal, roleDefinitionId, request));
    }

    @PostMapping("/{roleDefinitionId}/retirement")
    @Operation(summary = "Retire custom role without hard deleting (ROLE_CUSTOM_MANAGE / Rule 17)")
    public ResponseEntity<RoleDetailResponse> retireCustomRole(
            @PathVariable UUID tenantId,
            @PathVariable UUID roleDefinitionId,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(roleCustomService.retireCustomRole(principal, roleDefinitionId));
    }

    private RbacPrincipal extractTenantPrincipal(UUID tenantId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Tenant credentials required; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in path does not match authenticated tenant.");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}
