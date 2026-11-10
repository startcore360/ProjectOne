package com.edu360.api.platformops.dataprotection;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.CreateRetentionChangeRequest;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.DecideDeletionRequestDto;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.ExportRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.RetentionChangeRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.RetentionPolicyResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Tenant export and retention on the platform side. The old {@code DELETE /platform/tenants/{id}} is gone
 * (L-01): deletion is only the three-step chain under /platform/deletion-requests.
 * Retention follows D5: requestRetentionChange (TENANT_SETTINGS) and decideRetentionChange (DPO only).
 */
@RestController
@RequestMapping({"/v1/platform", "/api/platform"})
@RequiredArgsConstructor
public class PlatformDataProtectionController {

    private final PlatformDataProtectionService dataProtectionService;
    private final PlatformPermissionService permissionService;

    @PostMapping("/tenants/{tenantId}/export")
    public ResponseEntity<ExportRequestResponse> exportTenant(@PathVariable UUID tenantId, Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_EXPORT_REQUEST");
        return ResponseEntity.ok(dataProtectionService.exportTenant(tenantId, principal));
    }

    @GetMapping("/tenants/{tenantId}/export")
    public ResponseEntity<List<ExportRequestResponse>> listExports(@PathVariable UUID tenantId, Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_EXPORT_REQUEST");
        return ResponseEntity.ok(dataProtectionService.listExports(tenantId));
    }

    @GetMapping("/tenants/{tenantId}/retention")
    public ResponseEntity<List<RetentionPolicyResponse>> getRetention(@PathVariable UUID tenantId, Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorizeAny(principal, List.of("RETENTION_CHANGE_APPROVE", "TENANT_SETTINGS"));
        return ResponseEntity.ok(dataProtectionService.getRetentionPolicies(tenantId));
    }

    /** D5 requestRetentionChange. */
    @PostMapping("/tenants/{tenantId}/retention-change-requests")
    public ResponseEntity<RetentionChangeRequestResponse> requestRetentionChange(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateRetentionChangeRequest req,
            Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_SETTINGS");
        return ResponseEntity.status(HttpStatus.CREATED).body(dataProtectionService.requestRetentionChange(tenantId, req, principal));
    }

    @GetMapping("/retention-change-requests")
    public ResponseEntity<List<RetentionChangeRequestResponse>> listRetentionChangeRequests(Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorizeAny(principal, List.of("RETENTION_CHANGE_APPROVE", "TENANT_SETTINGS"));
        return ResponseEntity.ok(dataProtectionService.listRetentionChangeRequests());
    }

    /** D5 decideRetentionChange: Data Protection Officer only; never the requester. */
    @PostMapping("/retention-change-requests/{id}/decision")
    public ResponseEntity<RetentionChangeRequestResponse> decideRetentionChange(
            @PathVariable UUID id,
            @Valid @RequestBody DecideDeletionRequestDto request,
            Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "RETENTION_CHANGE_APPROVE");
        return ResponseEntity.ok(dataProtectionService.decideRetentionChange(id, request.decision(), request.remark(), principal));
    }
}
