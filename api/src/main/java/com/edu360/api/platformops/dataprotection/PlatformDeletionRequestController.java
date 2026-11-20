package com.edu360.api.platformops.dataprotection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.idempotency.PlatformIdempotentExecutor;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.ConfirmDeletionRequestDto;
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
 * E8-S5: the mapped endpoints require an Idempotency-Key (all three) and If-Match (confirm), as D5 marks them.
 * The header-less overloads are internal/test entry points only and are not mapped to HTTP.
 */
@RestController
@RequestMapping({"/v1/platform/deletion-requests", "/api/platform/deletion-requests"})
@RequiredArgsConstructor
@Tag(name = "Platform Deletion Requests", description = "Multi-step DPO tenant deletion approval chain [AP-23, CL-07, C8, D1]")
public class PlatformDeletionRequestController {

    private final PlatformDataProtectionService dataProtectionService;
    private final PlatformPermissionService permissionService;
    private final PlatformIdempotentExecutor idempotentExecutor;

    @PostMapping
    @Operation(summary = "Request tenant deletion (TENANT_DELETION_REQUEST; Idempotency-Key)")
    public ResponseEntity<DeletionRequestResponse> requestDeletion(
            @Valid @RequestBody CreateDeletionRequestDto request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_DELETION_REQUEST");
        return idempotentExecutor.execute(principal.platformUserId(), "requestTenantDeletion", idempotencyKey,
                request, HttpStatus.CREATED, DeletionRequestResponse.class,
                () -> dataProtectionService.requestTenantDeletionPlatform(request.tenantId(), request.origin(), principal));
    }

    /** Internal / test path: no Idempotency-Key. */
    public ResponseEntity<DeletionRequestResponse> requestDeletion(CreateDeletionRequestDto request, Authentication auth) {
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
        permissionService.authorizeAny(principal, List.of("TENANT_DELETION_REQUEST", "TENANT_DELETION_APPROVE"));
        return ResponseEntity.ok(dataProtectionService.listDeletionRequests());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get single deletion request details")
    public ResponseEntity<DeletionRequestResponse> getDeletionRequest(
            @PathVariable UUID id,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorizeAny(principal, List.of("TENANT_DELETION_REQUEST", "TENANT_DELETION_APPROVE"));
        return ResponseEntity.ok(dataProtectionService.getDeletionRequest(id));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Product Owner confirms tenant deletion request (TENANT_DELETION_CONFIRM; Idempotency-Key "
            + "+ If-Match); optional body waives the retention wait with a reason (PD-7)")
    public ResponseEntity<DeletionRequestResponse> confirmDeletion(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ConfirmDeletionRequestDto request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_DELETION_CONFIRM");
        boolean waive = request != null && request.waiveRetentionWait();
        String reason = request == null ? null : request.waiverReason();
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("id", id);
        fingerprint.put("waive", waive);
        fingerprint.put("reason", reason);
        return idempotentExecutor.execute(principal.platformUserId(), "confirmTenantDeletion", idempotencyKey,
                fingerprint, HttpStatus.OK, DeletionRequestResponse.class,
                () -> dataProtectionService.confirmTenantDeletion(id, waive, reason, principal, ifMatch));
    }

    /** Internal / test path: no HTTP headers, no version check. */
    public ResponseEntity<DeletionRequestResponse> confirmDeletion(
            UUID id, ConfirmDeletionRequestDto request, Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_DELETION_CONFIRM");
        boolean waive = request != null && request.waiveRetentionWait();
        String reason = request == null ? null : request.waiverReason();
        return ResponseEntity.ok(dataProtectionService.confirmTenantDeletion(id, waive, reason, principal));
    }

    /** Body-less confirmation (no waiver); kept for callers and tests that confirm without options. */
    public ResponseEntity<DeletionRequestResponse> confirmDeletion(UUID id, Authentication auth) {
        return confirmDeletion(id, null, auth);
    }

    @PostMapping("/{id}/decision")
    @Operation(summary = "Data Protection Officer decides deletion request (TENANT_DELETION_APPROVE; Idempotency-Key)")
    public ResponseEntity<DeletionRequestResponse> decideDeletion(
            @PathVariable UUID id,
            @Valid @RequestBody DecideDeletionRequestDto request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_DELETION_APPROVE");
        return idempotentExecutor.execute(principal.platformUserId(), "decideTenantDeletion", idempotencyKey,
                Map.of("id", id, "request", request), HttpStatus.OK, DeletionRequestResponse.class,
                () -> dataProtectionService.decideTenantDeletion(id, request.decision(), request.remark(), principal));
    }

    /** Internal / test path: no Idempotency-Key. */
    public ResponseEntity<DeletionRequestResponse> decideDeletion(
            UUID id, DecideDeletionRequestDto request, Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "TENANT_DELETION_APPROVE");
        return ResponseEntity.ok(dataProtectionService.decideTenantDeletion(id, request.decision(), request.remark(), principal));
    }
}
