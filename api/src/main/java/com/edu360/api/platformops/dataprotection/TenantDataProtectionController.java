package com.edu360.api.platformops.dataprotection;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.DeletionRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.ExportRequestResponse;
import com.edu360.api.rbac.PermissionDecision;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

import lombok.RequiredArgsConstructor;

/**
 * Tenant-side data-protection requests and history (E5-S6).
 *
 * Authorization is action-based: role names are deliberately not inspected here, so custom
 * tenant role templates work through the same PermissionEvaluatorService reference data.
 */
@RestController
@RequestMapping({"/v1/tenants/{tenantId}", "/api/tenants/{tenantId}"})
@RequiredArgsConstructor
public class TenantDataProtectionController {

    private static final String EXPORT_ACTION = "TENANT_EXPORT_REQUEST";
    private static final String DELETION_ACTION = "TENANT_DELETION_REQUEST";

    private final TenantDataProtectionService dataProtectionService;
    private final PermissionEvaluatorService permissionEvaluatorService;

    @PostMapping("/tenant-export-requests")
    public ResponseEntity<ExportRequestResponse> requestExport(
            @PathVariable UUID tenantId, Authentication auth) {
        TenantPrincipal principal = requireTenantUser(tenantId, auth);
        authorize(principal, EXPORT_ACTION);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dataProtectionService.requestExport(principal.tenantId(), principal.userAccountId()));
    }

    @GetMapping("/tenant-export-requests")
    public ResponseEntity<List<ExportRequestResponse>> exportHistory(
            @PathVariable UUID tenantId, Authentication auth) {
        TenantPrincipal principal = requireTenantUser(tenantId, auth);
        authorizeEitherHistoryAction(principal);
        return ResponseEntity.ok(dataProtectionService.listExports(principal.tenantId()));
    }

    @GetMapping("/tenant-export-requests/{id}/download")
    public ResponseEntity<InputStreamResource> downloadExport(
            @PathVariable UUID tenantId, @PathVariable UUID id, Authentication auth) {
        TenantPrincipal principal = requireTenantUser(tenantId, auth);
        authorize(principal, EXPORT_ACTION);
        InputStream stream = dataProtectionService.openDownload(principal.tenantId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("tenant-export-" + id + ".zip").build().toString())
                .body(new InputStreamResource(stream));
    }

    @PostMapping("/tenant-deletion-requests")
    public ResponseEntity<DeletionRequestResponse> requestDeletion(
            @PathVariable UUID tenantId, Authentication auth) {
        TenantPrincipal principal = requireTenantUser(tenantId, auth);
        authorize(principal, DELETION_ACTION);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dataProtectionService.requestDeletion(principal.tenantId(), principal.userAccountId()));
    }

    @GetMapping("/tenant-deletion-requests")
    public ResponseEntity<List<DeletionRequestResponse>> deletionHistory(
            @PathVariable UUID tenantId, Authentication auth) {
        TenantPrincipal principal = requireTenantUser(tenantId, auth);
        authorizeEitherHistoryAction(principal);
        return ResponseEntity.ok(dataProtectionService.listDeletions(principal.tenantId()));
    }

    private void authorize(TenantPrincipal principal, String action) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                action, RbacScope.tenant(), RbacTarget.NONE);
    }

    /** History is visible to a user holding either of the two data-protection request actions. */
    private void authorizeEitherHistoryAction(TenantPrincipal principal) {
        RbacPrincipal rbacPrincipal = new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        PermissionDecision export = permissionEvaluatorService.can(
                rbacPrincipal, EXPORT_ACTION, RbacScope.tenant(), RbacTarget.NONE);
        if (export.allowed()) {
            return;
        }
        PermissionDecision deletion = permissionEvaluatorService.can(
                rbacPrincipal, DELETION_ACTION, RbacScope.tenant(), RbacTarget.NONE);
        if (deletion.allowed()) {
            return;
        }
        // Delegate the final denial to authorize so the evaluator's standard 403/active-role
        // response semantics remain consistent with other tenant controllers.
        permissionEvaluatorService.authorize(
                rbacPrincipal, EXPORT_ACTION, RbacScope.tenant(), RbacTarget.NONE);
    }

    private TenantPrincipal requireTenantUser(UUID tenantId, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("This is a tenant-side action; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the URL does not match the authenticated tenant.");
        }
        return principal;
    }
}
