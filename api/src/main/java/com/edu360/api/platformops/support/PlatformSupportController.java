package com.edu360.api.platformops.support;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.support.dto.AssumeSupportSessionResponse;
import com.edu360.api.platformops.support.dto.SupportTicketResponse;
import com.edu360.api.platformops.support.dto.UpdateSupportTicketRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform", "/api/platform"})
@RequiredArgsConstructor
public class PlatformSupportController {

    private final PlatformSupportService supportService;
    private final PlatformPermissionService permissionService;

    @GetMapping("/support-tickets")
    public ResponseEntity<List<SupportTicketResponse>> listTickets(
            @RequestParam(required = false) UUID tenantId,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "SUPPORT_TICKET_MANAGE");
        return ResponseEntity.ok(supportService.listTickets(tenantId));
    }

    @GetMapping("/support-tickets/{id}")
    public ResponseEntity<SupportTicketResponse> getTicket(@PathVariable UUID id, Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "SUPPORT_TICKET_MANAGE");
        return ResponseEntity.ok(supportService.getTicket(id));
    }

    @PatchMapping("/support-tickets/{id}")
    public ResponseEntity<SupportTicketResponse> updateTicket(
            @PathVariable UUID id,
            @RequestBody UpdateSupportTicketRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "SUPPORT_TICKET_MANAGE");
        return ResponseEntity.ok(supportService.updateTicket(id, req, principal));
    }

    @PostMapping("/support-sessions")
    public ResponseEntity<AssumeSupportSessionResponse> assumeSupportSession(
            @RequestParam UUID ticketId,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "SUPPORT_ACCESS_USE");
        return ResponseEntity.ok(supportService.assumeSupportSession(ticketId, principal));
    }

    @GetMapping("/support-access-grants")
    public ResponseEntity<List<com.edu360.api.platformops.support.dto.SupportAccessGrantResponse>> listGrants(
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "SUPPORT_ACCESS_USE");
        return ResponseEntity.ok(supportService.listActiveGrants());
    }
}
