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
import com.edu360.api.identity.UserAccountService;
import com.edu360.api.platformops.support.dto.CreateSupportTicketRequest;
import com.edu360.api.platformops.support.dto.SupportTicketResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * E1-S5 (WF-01): "Request upgrade" from the Users page. It is an ordinary support ticket, raised
 * through the same path and permission (SUPPORT_TICKET_RAISE), with category BILLING (OD-15
 * provisional list) and subject "Upgrade request", so it shows in the platform Tickets queue under
 * the Billing filter. The current usage is written into the ticket so Billing sees the numbers.
 */
@RestController
@RequestMapping({"/v1/tenants/{tenantId}", "/api/tenants/{tenantId}"})
@Tag(name = "Support", description = "Ask the platform for a plan upgrade")
public class TenantUpgradeRequestController {

    public record UpgradeRequest(String note) {
    }

    private final PlatformSupportService supportService;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final UserAccountService userAccountService;

    public TenantUpgradeRequestController(PlatformSupportService supportService,
                                          PermissionEvaluatorService permissionEvaluatorService,
                                          UserAccountService userAccountService) {
        this.supportService = supportService;
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.userAccountService = userAccountService;
    }

    @PostMapping("/upgrade-requests")
    @Operation(summary = "Ask for a plan upgrade: raises a BILLING support ticket (SUPPORT_TICKET_RAISE)")
    public ResponseEntity<SupportTicketResponse> requestUpgrade(@PathVariable UUID tenantId,
                                                                @RequestBody(required = false) UpgradeRequest body,
                                                                Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)
                || !tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("Upgrade requests are made by signed-in users of this tenant.");
        }
        permissionEvaluatorService.authorize(new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "SUPPORT_TICKET_RAISE", RbacScope.tenant(), RbacTarget.NONE);

        UserAccountService.UserLimit usage = userAccountService.currentUserLimit(tenantId);
        String note = body == null || body.note() == null ? "" : body.note().trim();
        String description = "Upgrade requested from the Users page. Accounts in use: " + usage.active()
                + (usage.limit() == null ? " (no limit set)." : " of " + usage.limit() + " (" + usage.percent() + "%).")
                + (note.isEmpty() ? "" : "\n\nNote from the requester: " + note.substring(0, Math.min(note.length(), 2000)));
        CreateSupportTicketRequest ticket = new CreateSupportTicketRequest("Upgrade request", description, null, "BILLING");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supportService.raiseTicket(tenantId, ticket, principal.userAccountId(), principal.displayName()));
    }
}
