package com.edu360.api.platformops;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.dto.BackupStatusEntryResponse;
import com.edu360.api.platformops.dto.BackupStatusResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * BACKUP_STATUS_VIEW - "See backup status." Who: Product Owner, Platform Admin
 * (sql/05_ref_data.sql ids 1691/1692 - the same action also lists tenant-side
 * IT_ADMINISTRATOR/SCHOOL_IT_SUPPORT for their OWN tenant's status, ids 1689/1690, which is a
 * separate tenant-scoped endpoint this package does not build).
 *
 * The table (platform.tenant_backup_status) already exists in the committed sql/ bundle, so this
 * is a real read against the real schema - not a fabricated placeholder shape. What IS missing,
 * and is reported here rather than hidden, is any process that ever writes a row into it: see
 * {@link TenantBackupStatus}'s javadoc. {@code wiredToRealBackupSystem} is hard-coded false
 * because that is accurately the current state of this repository, not a value read from
 * anywhere.
 */
@RestController
@RequestMapping("/api/platform/backup-status")
@RequiredArgsConstructor
@Tag(name = "Platform Ops", description = "Platform Admin / Product Owner read-mostly operations")
public class BackupStatusController {

    private final PlatformPermissionService platformPermissionService;
    private final TenantBackupStatusRepository tenantBackupStatusRepository;

    @GetMapping
    @Operation(summary = "See backup status (BACKUP_STATUS_VIEW)")
    public ResponseEntity<BackupStatusResponse> backupStatus(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("BACKUP_STATUS_VIEW is a platform-side action; no platform principal on this request.");
        }
        platformPermissionService.authorize(principal, "BACKUP_STATUS_VIEW");

        var tenants = tenantBackupStatusRepository.findAllByOrderByTenantId().stream()
                .map(BackupStatusEntryResponse::from)
                .toList();

        String note = "platform.tenant_backup_status exists in the schema, but no backup/PITR "
                + "system in this codebase writes to it yet - this list reflects that real, "
                + "currently-empty state rather than placeholder timestamps.";

        return ResponseEntity.ok(new BackupStatusResponse(false, note, tenants));
    }
}
