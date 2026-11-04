package com.edu360.api.platformops;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WHAT: a read-only view of platform.tenant_backup_status (sql/01_schema.sql line ~1054) -
 * "Backup / PITR status per tenant; IT staff see status only". The table IS already in the
 * committed sql/ bundle, so BACKUP_STATUS_VIEW is not blocked on a missing migration.
 *
 * REAL GAP (flagged, not worked around): no job or service anywhere in this codebase INSERTs or
 * UPDATEs this table - there is no actual backup/PITR system wired to EDU 360 yet (confirmed:
 * no reference to tenant_backup_status or any backup process in api/src/main/java). So in the
 * current dev/test environment this table has zero rows and
 * {@code BackupStatusController} will correctly report that, rather than inventing timestamps.
 */
@Entity
@Table(schema = "platform", name = "tenant_backup_status")
@Getter
@NoArgsConstructor
public class TenantBackupStatus {

    @Id
    @Column(name = "tenant_backup_status_id")
    private UUID tenantBackupStatusId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "last_daily_backup_at")
    private Instant lastDailyBackupAt;

    @Column(name = "last_backup_outcome")
    private String lastBackupOutcome;

    @Column(name = "earliest_restorable_at")
    private Instant earliestRestorableAt;

    @Column(name = "latest_restorable_at")
    private Instant latestRestorableAt;

    @Column(name = "last_restore_test_at")
    private Instant lastRestoreTestAt;

    @Column(name = "last_restore_test_outcome")
    private String lastRestoreTestOutcome;
}
