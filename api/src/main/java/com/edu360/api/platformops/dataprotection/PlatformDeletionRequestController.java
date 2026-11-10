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
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.CreateDeletionRequestDto;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.DecideDeletionRequestDto;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.DeletionRequestResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller exposing D5 multi-step tenant deletion chain under /platform/deletion-requests:
 * Request (PO) -> Confirm (PO) -> Decide (DPO). Approval locks the tenant now and marks the purge pending (PD-7).
 * Enforces C8 / D1 / ck_deletion_request_r4.
 */
@RestController
@RequestMapping({"/v1/platform/deletion-requests", "/api/platform/deletion-requests"})
@RequiredArgsConstructor
@Tag(name = "Platform Deletion Requests", description = "Multi-step DPO tenant deletion approval chain [AP-23, CL-07, C8, D1]")
public class PlatformDeletionRequestController {

    private final PlatformDataProtectionService dataProtectionService;
    private final PlatformPermissionService permissionService;

    @PostMapping
    @Operation(summary = "Request tenant deletion (TENANT_DELETION_REQUEST)")
    public ResponseEntity<DeletionRequestResponse> requestDeletion(
            @Valid @RequestBody CreateDeletionRequestDto request,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_DELETION_REQUEST");
        DeletionRequestResponse response = dataProtectionService.requestTenantDeletionPlatform(
                request.tenantId(), request.origin(), principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all deletion requests")
    public ResponseEntity<List<DeletionRequestResponse>> listDeletionRequests(Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_DELETION_REQUEST");
        return ResponseEntity.ok(dataProtectionService.listDeletionRequests());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get single deletion request details")
    public ResponseEntity<DeletionRequestResponse> getDeletionRequest(
            @PathVariable UUID id,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_DELETION_REQUEST");
        return ResponseEntity.ok(dataProtectionService.getDeletionRequest(id));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Product Owner confirms tenant deletion request (TENANT_DELETION_CONFIRM)")
    public ResponseEntity<DeletionRequestResponse> confirmDeletion(
            @PathVariable UUID id,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_DELETION_CONFIRM");
        return ResponseEntity.ok(dataProtectionService.confirmTenantDeletion(id, principal));
    }

    @PostMapping("/{id}/decision")
    @Operation(summary = "Data Protection Officer decides deletion request (TENANT_DELETION_APPROVE)")
    public ResponseEntity<DeletionRequestResponse> decideDeletion(
            @PathVariable UUID id,
            @Valid @RequestBody DecideDeletionRequestDto request,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_DELETION_APPROVE");
        return ResponseEntity.ok(dataProtectionService.decideTenantDeletion(id, request.decision(), request.remark(), principal));
    }
}
