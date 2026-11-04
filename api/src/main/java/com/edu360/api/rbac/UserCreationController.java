package com.edu360.api.rbac;

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
import com.edu360.api.rbac.dto.CreateUserCheckRequest;
import com.edu360.api.rbac.dto.CreateUserCheckResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * WHAT: a minimal proof that PermissionEvaluatorService is wired end-to-end into a real HTTP
 * request, using the Users & roles epic's USER_CREATE action - the natural first candidate per
 * the task brief. This is deliberately NOT the full USER_CREATE story: there is no user
 * actually created (no Keycloak provisioning, no app.user_account insert, no audit log entry,
 * no request-body validation beyond scope shape) - only the authorization decision itself. The
 * full story needs the role-provisioning epic (app.role_definition/app.role_action rows
 * actually populated for a tenant) to exist first, which this change does not build.
 *
 * WHY /api/tenants/{tenantId}/users, not /v1/tenants/{tenantId}/users as the D5 OpenAPI draft
 * names it: every existing route in this codebase lives under /api (AuthController) - there is
 * no /v1 prefix anywhere yet, and SecurityConfig's anyRequest().authenticated() already covers
 * any new path without changes, so this follows the app's OWN current convention rather than
 * the target spec's path prefix. Flagged here rather than silently picked.
 *
 * WHY tenantId is taken from the URL AND re-checked against the authenticated principal's own
 * tenantId, never trusted from the path alone (Addendum rule 13, tenant isolation; CLAUDE.md
 * tenant_id handling): a path parameter is attacker-controlled input. The principal's tenantId
 * comes from TenantContext's already-verified token, same as everywhere else in this app.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Proof-of-integration endpoint for the RBAC action evaluator (USER_CREATE)")
public class UserCreationController {

    private final PermissionEvaluatorService permissionEvaluatorService;

    @PostMapping
    @Operation(summary = "Check USER_CREATE authorization for a given scope (evaluator proof-of-integration, not full user creation)")
    public ResponseEntity<CreateUserCheckResponse> checkCreateUser(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateUserCheckRequest request,
            Authentication authentication) {

        if (!(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("USER_CREATE is a tenant-side action; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the URL does not match the authenticated tenant.");
        }

        RbacScope scope = switch (request.scopeType()) {
            case TENANT -> RbacScope.tenant();
            case INSTITUTION -> RbacScope.institution(request.institutionId());
            case DEPARTMENT -> RbacScope.department(request.institutionId(), request.departmentId());
        };

        permissionEvaluatorService.authorize(
                new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "USER_CREATE",
                scope,
                RbacTarget.NONE);

        return ResponseEntity.ok(new CreateUserCheckResponse("USER_CREATE", "ALLOWED",
                "Authorized to create a user in this scope. (Proof of the RBAC evaluator only - no user was created.)"));
    }
}
