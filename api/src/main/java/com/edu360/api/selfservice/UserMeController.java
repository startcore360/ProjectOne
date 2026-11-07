package com.edu360.api.selfservice;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.selfservice.dto.DashboardPreferencesRequest;
import com.edu360.api.selfservice.dto.SwitchActiveRoleRequest;
import com.edu360.api.selfservice.dto.SwitchActiveRoleResponse;
import com.edu360.api.selfservice.dto.UserMeResponse;
import com.edu360.api.selfservice.dto.UserPermissionsResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller exposing self-identity, available role options, and active role switching:
 * - ROLE_SWITCH (Sprint 1 Story #6 / Rule HI-15)
 *
 * Mapped to both standard V4.0 path (/v1/tenants/{tenantId}/me/...) and convenience path (/api/me/...).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Self-service", description = "Current identity and active role switching")
public class UserMeController {

    private final UserMeService userMeService;

    @GetMapping({"/v1/tenants/{tenantId}/me", "/api/me"})
    @Operation(summary = "Get current authenticated identity, active role, and all available role options (ROLE_SWITCH)")
    public ResponseEntity<UserMeResponse> getMe(
            @PathVariable(value = "tenantId", required = false) UUID tenantId,
            Authentication authentication) {

        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        UserMeResponse response = userMeService.getMe(principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/v1/tenants/{tenantId}/me/permissions", "/api/me/permissions"})
    @Operation(summary = "Get active role code, scope, and allowed action codes for the authenticated user")
    public ResponseEntity<UserPermissionsResponse> getPermissions(
            @PathVariable(value = "tenantId", required = false) UUID tenantId,
            Authentication authentication) {

        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        UserPermissionsResponse response = userMeService.getPermissions(principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/v1/tenants/{tenantId}/me/active-role", "/api/me/active-role"})
    @Operation(summary = "Switch the current active role (ROLE_SWITCH / Rule HI-15)")
    public ResponseEntity<SwitchActiveRoleResponse> switchActiveRole(
            @PathVariable(value = "tenantId", required = false) UUID tenantId,
            @Valid @RequestBody SwitchActiveRoleRequest request,
            Authentication authentication) {

        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        SwitchActiveRoleResponse response = userMeService.switchActiveRole(principal, request.assignmentId());
        return ResponseEntity.ok(response);
    }

    @PutMapping({"/v1/tenants/{tenantId}/me/dashboard-preferences", "/api/me/dashboard-preferences"})
    @Operation(summary = "Save user dashboard preferences per active role")
    public ResponseEntity<Map<String, String>> saveDashboardPreferences(
            @PathVariable(value = "tenantId", required = false) UUID tenantId,
            @RequestBody DashboardPreferencesRequest request,
            Authentication authentication) {

        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        userMeService.saveDashboardPreferences(principal, request);
        return ResponseEntity.ok(Map.of("message", "Dashboard preferences updated successfully."));
    }

    private TenantPrincipal extractPrincipal(Authentication auth, UUID tenantId) {
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Authentication required.");
        }
        if (tenantId != null && !tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("Tenant ID in path does not match authenticated user's tenant.");
        }
        return principal;
    }
}
