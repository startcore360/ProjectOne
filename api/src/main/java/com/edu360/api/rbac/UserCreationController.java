package com.edu360.api.rbac;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserAccountService;
import com.edu360.api.rbac.dto.CreateUserCheckRequest;
import com.edu360.api.rbac.dto.CreateUserCheckResponse;
import com.edu360.api.rbac.dto.CreateUserRequest;
import com.edu360.api.rbac.dto.CreateUserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller exposing user creation and authorization endpoints:
 * - USER_CREATE (Sprint 1 Story #9 / 12.3, 13.1, MX-13)
 *
 * Mapped to both /v1/tenants/{tenantId}/users (OpenAPI target)
 * and /api/tenants/{tenantId}/users (current app convention).
 */
@RestController
@RequestMapping({"/v1/tenants/{tenantId}/users", "/api/tenants/{tenantId}/users"})
@RequiredArgsConstructor
@Tag(name = "Users", description = "User creation and provisioning (USER_CREATE)")
public class UserCreationController {

    private final UserAccountService userAccountService;
    private final PermissionEvaluatorService permissionEvaluatorService;

    @PostMapping
    @Operation(summary = "Create and provision a new user account (USER_CREATE)")
    public ResponseEntity<CreateUserResponse> createUser(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        CreateUserResponse response = userAccountService.createUser(principal, request);
        return ResponseEntity.created(URI.create("/api/tenants/" + tenantId + "/users/" + response.userAccountId()))
                .body(response);
    }

    @PostMapping("/check")
    @Operation(summary = "Check USER_CREATE authorization for a given scope")
    public ResponseEntity<CreateUserCheckResponse> checkCreateUser(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateUserCheckRequest request,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);

        RbacScope scope = switch (request.scopeType()) {
            case TENANT -> RbacScope.tenant();
            case INSTITUTION -> RbacScope.institution(request.institutionId());
            case DEPARTMENT -> RbacScope.department(request.institutionId(), request.departmentId());
        };

        permissionEvaluatorService.authorize(
                principal,
                "USER_CREATE",
                scope,
                RbacTarget.NONE);

        return ResponseEntity.ok(new CreateUserCheckResponse("USER_CREATE", "ALLOWED",
                "Authorized to create a user in this scope."));
    }

    private RbacPrincipal extractTenantPrincipal(UUID tenantId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("USER_CREATE is a tenant-side action; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the URL does not match the authenticated tenant.");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}
