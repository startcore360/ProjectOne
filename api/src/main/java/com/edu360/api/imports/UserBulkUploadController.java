package com.edu360.api.imports;

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

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.imports.dto.ImportJobResponse;
import com.edu360.api.imports.dto.ImportRowErrorResponse;
import com.edu360.api.imports.dto.ImportTemplateResponse;
import com.edu360.api.imports.dto.StartImportRequest;
import com.edu360.api.imports.dto.UserImportRequest;
import com.edu360.api.rbac.RbacPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller exposing endpoints for:
 * - USER_BULK_UPLOAD (Sprint 1 Story #10 / MD-26, MD-27, R2-34)
 *
 * Mapped to both /v1/tenants/{tenantId} and /api/tenants/{tenantId}.
 */
@RestController
@RequestMapping({"/v1/tenants/{tenantId}", "/api/tenants/{tenantId}"})
@RequiredArgsConstructor
@Tag(name = "User bulk upload", description = "Checked bulk user imports (USER_BULK_UPLOAD / Rule MD-27)")
public class UserBulkUploadController {

    private final UserBulkUploadService userBulkUploadService;

    @GetMapping("/import-templates/{kind}")
    @Operation(summary = "Download standard CSV template for bulk uploads (Rule MD-26)")
    public ResponseEntity<ImportTemplateResponse> getImportTemplate(
            @PathVariable UUID tenantId,
            @PathVariable String kind) {
        return ResponseEntity.ok(userBulkUploadService.getImportTemplate(kind));
    }

    @PostMapping("/user-imports")
    @Operation(summary = "Upload and pre-validate user import CSV (USER_BULK_UPLOAD / Rule MD-27)")
    public ResponseEntity<ImportJobResponse> createUserImport(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UserImportRequest request,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        ImportJobResponse response = userBulkUploadService.createUserImport(principal, tenantId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/imports/{importJobId}")
    @Operation(summary = "Get status and counts of an import job (Rule MD-27)")
    public ResponseEntity<ImportJobResponse> getImportJob(
            @PathVariable UUID tenantId,
            @PathVariable UUID importJobId,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(userBulkUploadService.getImportJob(principal, tenantId, importJobId));
    }

    @GetMapping("/imports/{importJobId}/errors")
    @Operation(summary = "List row-level validation errors with reasons (Rule MD-27)")
    public ResponseEntity<List<ImportRowErrorResponse>> listImportErrors(
            @PathVariable UUID tenantId,
            @PathVariable UUID importJobId,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(userBulkUploadService.listImportErrors(principal, tenantId, importJobId));
    }

    @PostMapping("/imports/{importJobId}/start")
    @Operation(summary = "Execute import under chosen mode: CLEAN_ONLY vs GOOD_ROWS_ONLY (Rule MD-27)")
    public ResponseEntity<ImportJobResponse> startImport(
            @PathVariable UUID tenantId,
            @PathVariable UUID importJobId,
            @Valid @RequestBody StartImportRequest request,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        ImportJobResponse response = userBulkUploadService.startImport(principal, tenantId, importJobId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    private RbacPrincipal extractTenantPrincipal(UUID tenantId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Import operations require authenticated tenant principal.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the path does not match the authenticated tenant.");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}
