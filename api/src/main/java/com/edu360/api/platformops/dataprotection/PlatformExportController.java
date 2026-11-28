package com.edu360.api.platformops.dataprotection;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platformops.PlatformPermissionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * E5-S3 export download, plus the count-only pending indicator the Product Owner dashboard needs (E9-S1).
 *
 * The pending count returns exactly {"count": n}: export requests REQUESTED + deletion requests PENDING_DPO +
 * retention change requests REQUESTED. It reveals no tenant, request or requester detail and grants no access
 * to the DPO queues; the approve actions stay DPO-only (TPA-04).
 */
@RestController
@RequestMapping({"/v1/platform", "/api/platform"})
@Tag(name = "Platform Data Export", description = "Tenant export download and pending data-protection count [CL-09, E5-S3]")
public class PlatformExportController {

    private final PlatformPermissionService permissionService;
    private final ExportRequestLifecycleService lifecycleService;
    private final ExportRequestRepository exportRequests;
    private final DeletionRequestRepository deletionRequests;
    private final RetentionChangeRequestRepository retentionChangeRequests;

    public PlatformExportController(PlatformPermissionService permissionService,
            ExportRequestLifecycleService lifecycleService, ExportRequestRepository exportRequests,
            DeletionRequestRepository deletionRequests, RetentionChangeRequestRepository retentionChangeRequests) {
        this.permissionService = permissionService;
        this.lifecycleService = lifecycleService;
        this.exportRequests = exportRequests;
        this.deletionRequests = deletionRequests;
        this.retentionChangeRequests = retentionChangeRequests;
    }

    @GetMapping("/data-protection/pending-count")
    @Operation(summary = "Count of data-protection requests awaiting the DPO (no details)")
    public ResponseEntity<Map<String, Long>> pendingCount(Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorizeAny(principal, List.of("TENANT_EXPORT_REQUEST", "TENANT_DELETION_REQUEST"));
        long count = exportRequests.countByStatus("REQUESTED")
                + deletionRequests.countByStatus("PENDING_DPO")
                + retentionChangeRequests.countByStatus("REQUESTED");
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/export-requests/{id}/download")
    @Operation(summary = "Download a READY tenant export ZIP (410 EXPORT_EXPIRED after 24 hours)")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id, Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorizeAny(principal, List.of("TENANT_EXPORT_REQUEST", "TENANT_EXPORT_APPROVE"));
        InputStream input = lifecycleService.openDownload(id, principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("tenant-export-" + id + ".zip").build().toString())
                .body(new InputStreamResource(input));
    }
}
