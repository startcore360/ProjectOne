package com.edu360.api.platformops;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Dev-profile backup-agent ingress. Production ingestion is reserved for the service-auth integration. */
@RestController
@RequestMapping({"/api/platform/backup-status", "/v1/platform/backup-status"})
@Profile("dev")
public class BackupStatusIngestController {
    private static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final JdbcTemplate jdbcTemplate;

    public BackupStatusIngestController(@Qualifier("platformDataSource") DataSource platformDataSource) {
        this.jdbcTemplate = new JdbcTemplate(platformDataSource);
    }

    @Value("${app.backup.agent.secret:}")
    private String configuredSecret;

    public record BackupStatusUpdate(
            UUID tenantId,
            @NotNull Instant lastDailyBackupAt,
            @NotNull @Pattern(regexp = "SUCCESS|FAILED") String outcome,
            Instant earliestRestorableAt,
            Instant latestRestorableAt,
            Instant lastRestoreTestAt,
            @Pattern(regexp = "PASSED|FAILED") String lastRestoreTestOutcome) {}

    @PostMapping
    public void ingest(@RequestHeader("X-Backup-Agent-Secret") String suppliedSecret,
                       @Valid @RequestBody BackupStatusUpdate update) {
        if (configuredSecret == null || configuredSecret.isBlank()
                || !MessageDigest.isEqual(configuredSecret.getBytes(StandardCharsets.UTF_8),
                        suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid backup-agent credential");
        }
        List<UUID> tenantIds = update.tenantId() == null
                ? jdbcTemplate.query("SELECT tenant_id FROM platform.tenant ORDER BY tenant_id",
                        (rs, row) -> rs.getObject(1, UUID.class))
                : List.of(update.tenantId());
        if (tenantIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No tenants are available for backup status");
        }
        for (UUID tenantId : tenantIds) {
            jdbcTemplate.update("""
                    INSERT INTO platform.tenant_backup_status
                      (tenant_backup_status_id, tenant_id, last_daily_backup_at, last_backup_outcome,
                       earliest_restorable_at, latest_restorable_at, last_restore_test_at,
                       last_restore_test_outcome, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id) DO UPDATE SET
                      last_daily_backup_at = EXCLUDED.last_daily_backup_at,
                      last_backup_outcome = EXCLUDED.last_backup_outcome,
                      earliest_restorable_at = EXCLUDED.earliest_restorable_at,
                      latest_restorable_at = EXCLUDED.latest_restorable_at,
                      last_restore_test_at = EXCLUDED.last_restore_test_at,
                      last_restore_test_outcome = EXCLUDED.last_restore_test_outcome,
                      updated_by = EXCLUDED.updated_by
                    """,
                    UUID.randomUUID(), tenantId, Timestamp.from(update.lastDailyBackupAt()), update.outcome(),
                    timestamp(update.earliestRestorableAt()), timestamp(update.latestRestorableAt()),
                    timestamp(update.lastRestoreTestAt()), update.lastRestoreTestOutcome(), SYSTEM_ACTOR, SYSTEM_ACTOR);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
