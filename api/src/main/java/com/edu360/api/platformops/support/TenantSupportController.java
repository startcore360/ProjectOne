package com.edu360.api.platformops.support;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.support.dto.CreateSupportTicketRequest;
import com.edu360.api.platformops.support.dto.GrantSupportAccessRequest;
import com.edu360.api.platformops.support.dto.SupportAccessGrantResponse;
import com.edu360.api.platformops.support.dto.SupportTicketResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The tenant side of support (D5 raiseSupportTicket, grantSupportAccess).
 *
 * WHY these are tenant endpoints and not under /platform: the tenant is the only party who may
 * approve access to its own data (GUARD_PLATFORM_ISOLATION, D1 MX-01). A platform principal is
 * rejected, so platform staff can never approve their own access. The tenant comes from the
 * verified token, checked against the path, and is never read from the request body.
 */
@RestController
@RequestMapping({"/v1/tenants/{tenantId}", "/api/tenants/{tenantId}"})
@RequiredArgsConstructor
public class TenantSupportController {

    private final PlatformSupportService supportService;
    private final PermissionEvaluatorService permissionEvaluatorService;

    /** Any signed-in user of the tenant may raise a ticket (D5, WF-10). */
    @PostMapping("/support-tickets")
    public ResponseEntity<SupportTicketResponse> raiseTicket(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateSupportTicketRequest req,
            Authentication auth
    ) {
        TenantPrincipal principal = requireTenantUser(tenantId, auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                supportService.raiseTicket(tenantId, req, principal.userAccountId(), principal.displayName()));
    }

    @PostMapping("/support-access-grants")
    public ResponseEntity<SupportAccessGrantResponse> grantAccess(
            @PathVariable UUID tenantId,
            @Valid @RequestBody GrantSupportAccessRequest req,
            Authentication auth
    ) {
        TenantPrincipal principal = requireTenantUser(tenantId, auth);
        RbacScope scope = req.institutionId() != null ? RbacScope.institution(req.institutionId()) : RbacScope.tenant();
        permissionEvaluatorService.authorize(
                new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "SUPPORT_ACCESS_GRANT",
                scope,
                RbacTarget.NONE);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supportService.grantAccess(tenantId, req, principal.userAccountId()));
    }

    @org.springframework.web.bind.annotation.GetMapping("/support-access-grants")
    public ResponseEntity<java.util.List<SupportAccessGrantResponse>> listGrants(
            @PathVariable UUID tenantId,
            Authentication auth
    ) {
        TenantPrincipal principal = requireTenantUser(tenantId, auth);
        permissionEvaluatorService.authorize(
                new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "SUPPORT_ACCESS_GRANT",
                RbacScope.tenant(),
                RbacTarget.NONE);
        return ResponseEntity.ok(supportService.listGrantsForTenant(tenantId));
    }

    @org.springframework.web.bind.annotation.PostMapping({"/support-access-grants/{grantId}/withdrawal", "/support-access-grants/{grantId}/withdraw"})
    public ResponseEntity<SupportAccessGrantResponse> withdrawGrant(
            @PathVariable UUID tenantId,
            @PathVariable UUID grantId,
            Authentication auth
    ) {
        TenantPrincipal principal = requireTenantUser(tenantId, auth);
        permissionEvaluatorService.authorize(
                new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "SUPPORT_ACCESS_GRANT",
                RbacScope.tenant(),
                RbacTarget.NONE);
        return ResponseEntity.ok(supportService.withdrawAccess(tenantId, grantId, principal.userAccountId()));
    }

    private TenantPrincipal requireTenantUser(UUID tenantId, Authentication auth) {
        if (!(auth.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("This is a tenant-side action; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the URL does not match the authenticated tenant.");
        }
        return principal;
    }
}
