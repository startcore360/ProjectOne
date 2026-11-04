package com.edu360.api.platformops.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.platformops.TenantBackupStatus;

/** One row from platform.tenant_backup_status, for BACKUP_STATUS_VIEW. */
public record BackupStatusEntryResponse(
        UUID tenantId,
        Instant lastDailyBackupAt,
        String lastBackupOutcome,
        Instant earliestRestorableAt,
        Instant latestRestorableAt,
        Instant lastRestoreTestAt,
        String lastRestoreTestOutcome) {

    public static BackupStatusEntryResponse from(TenantBackupStatus status) {
        return new BackupStatusEntryResponse(
                status.getTenantId(),
                status.getLastDailyBackupAt(),
                status.getLastBackupOutcome(),
                status.getEarliestRestorableAt(),
                status.getLatestRestorableAt(),
                status.getLastRestoreTestAt(),
                status.getLastRestoreTestOutcome());
    }
}
