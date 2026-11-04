package com.edu360.api.platformops.dto;

import java.util.List;

/**
 * Response for GET /api/platform/backup-status. {@code wiredToRealBackupSystem} is always false
 * today and {@code note} explains why - see TenantBackupStatus's javadoc. {@code tenants} is the
 * real (currently empty) content of platform.tenant_backup_status, never fabricated data.
 */
public record BackupStatusResponse(
        boolean wiredToRealBackupSystem,
        String note,
        List<BackupStatusEntryResponse> tenants) {
}
