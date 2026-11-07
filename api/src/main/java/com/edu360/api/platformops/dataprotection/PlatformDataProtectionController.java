package com.edu360.api.platformops.dataprotection;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.CreateRetentionChangeRequest;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.DeletionRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.ExportRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.RetentionChangeRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.RetentionPolicyResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform/tenants/{tenantId}", "/api/platform/tenants/{tenantId}"})
@RequiredArgsConstructor
public class PlatformDataProtectionController {

    private final PlatformDataProtectionService dataProtectionService;
    private final PlatformPermissionService permissionService;

    @PostMapping("/export")
    public ResponseEntity<ExportRequestResponse> exportTenant(
            @PathVariable UUID tenantId,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_EXPORT_REQUEST");
        return ResponseEntity.ok(dataProtectionService.exportTenant(tenantId, principal));
    }

    @GetMapping("/export")
    public ResponseEntity<List<ExportRequestResponse>> listExports(
            @PathVariable UUID tenantId,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_EXPORT_REQUEST");
        return ResponseEntity.ok(dataProtectionService.listExports(tenantId));
    }

    @DeleteMapping
    public ResponseEntity<DeletionRequestResponse> deleteTenant(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String origin,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_DELETION_REQUEST");
        return ResponseEntity.ok(dataProtectionService.deleteTenant(tenantId, origin, principal));
    }

    @GetMapping("/retention")
    public ResponseEntity<List<RetentionPolicyResponse>> getRetention(
            @PathVariable UUID tenantId,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "RETENTION_CHANGE_APPROVE");
        return ResponseEntity.ok(dataProtectionService.getRetentionPolicies(tenantId));
    }

    @PostMapping("/retention")
    public ResponseEntity<RetentionChangeRequestResponse> requestRetentionChange(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateRetentionChangeRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "RETENTION_CHANGE_APPROVE");
        return ResponseEntity.ok(dataProtectionService.requestRetentionChange(tenantId, req, principal));
    }
}
