package com.edu360.api.platformops;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.dto.BackupStatusEntryResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

import lombok.RequiredArgsConstructor;

/**
 * L-04 / D5 getTenantBackupStatus: a tenant's own backup status, for the roles that hold BACKUP_STATUS_VIEW at
 * tenant level (IT Administrator, School IT Support). Status only, no download (WF-33).
 *
 * Honest by construction: the row comes from platform.tenant_backup_status. No backup system writes that table yet,
 * so until one does the answer is {"connected": false} with a note, never invented timestamps. A URL that names a
 * different tenant looks like a missing tenant (404), as D5 asks for foreign records.
 */
@RestController
@RequestMapping({"/v1/tenants/{tenantId}/backup-status", "/api/tenants/{tenantId}/backup-status"})
@RequiredArgsConstructor
public class TenantBackupStatusController {

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final TenantBackupStatusRepository tenantBackupStatusRepository;

    public record TenantBackupStatusResponse(boolean connected, String note, BackupStatusEntryResponse status) {}

    @GetMapping
    public ResponseEntity<TenantBackupStatusResponse> backupStatus(@PathVariable UUID tenantId, Authentication auth) {
        if (!(auth.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("This is a tenant-side action; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new ResourceNotFoundException("Tenant not found: " + tenantId);
        }
        permissionEvaluatorService.authorize(
                new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "BACKUP_STATUS_VIEW",
                RbacScope.tenant(),
                RbacTarget.NONE);

        return ResponseEntity.ok(tenantBackupStatusRepository.findFirstByTenantId(tenantId)
                .map(row -> new TenantBackupStatusResponse(true, null, BackupStatusEntryResponse.from(row)))
                .orElseGet(() -> new TenantBackupStatusResponse(false,
                        "No backup system is connected to this tenant yet, so there is no backup status to show.", null)));
    }
}
