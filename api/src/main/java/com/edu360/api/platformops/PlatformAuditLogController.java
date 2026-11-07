package com.edu360.api.platformops;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.dto.AuditLogEntryResponse;
import com.edu360.api.platformops.dto.AuditLogPageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * PLATFORM_AUDIT_VIEW - "See platform audit logs." Who: Product Owner, Platform Admin
 * (sql/05_ref_data.sql ref.standard_role_action ids 1684/1685). Reads ONLY
 * platform.audit_outbox via {@link PlatformAuditOutboxEvent} - never app.audit_outbox (the
 * tenant-scoped table) and never the legacy audit.AuditLog "audit_logs" table. See
 * PlatformAuditOutboxEvent's javadoc for the real gap: nothing writes to platform.audit_outbox
 * yet, so this will return an empty page until a platform-action audit writer exists.
 */
@RestController
@RequestMapping({"/v1/platform/audit-logs", "/api/platform/audit-logs"})
@RequiredArgsConstructor
@Tag(name = "Platform Ops", description = "Platform Admin / Product Owner read-mostly operations")
public class PlatformAuditLogController {

    private final PlatformPermissionService platformPermissionService;
    private final PlatformAuditOutboxEventRepository auditOutboxEventRepository;

    @GetMapping
    @Operation(summary = "List platform-scoped audit events (PLATFORM_AUDIT_VIEW)")
    public ResponseEntity<AuditLogPageResponse> listAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {

        if (!(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("PLATFORM_AUDIT_VIEW is a platform-side action; no platform principal on this request.");
        }
        platformPermissionService.authorize(principal, "PLATFORM_AUDIT_VIEW");

        int safeSize = Math.min(Math.max(size, 1), 200);
        Page<PlatformAuditOutboxEvent> result = auditOutboxEventRepository
                .findAllByOrderByOccurredAtDesc(PageRequest.of(Math.max(page, 0), safeSize));

        return ResponseEntity.ok(new AuditLogPageResponse(
                result.getContent().stream().map(AuditLogEntryResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()));
    }
}
