package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.edu360.api.platformops.dto.BackupStatusEntryResponse;

class BackupStatusFreshnessTest {
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    @Test void missingBackupIsStale() {
        assertThat(response(null, "SUCCESS").stale()).isTrue();
    }

    @Test void failedBackupIsStaleEvenWhenRecent() {
        assertThat(response(NOW.minusSeconds(60), "FAILED").stale()).isTrue();
    }

    @Test void recentSuccessfulBackupIsFresh() {
        assertThat(response(NOW.minus(Duration.ofHours(35)), "SUCCESS").stale()).isFalse();
    }

    @Test void successfulBackupPastThresholdIsStale() {
        assertThat(response(NOW.minus(Duration.ofHours(37)), "SUCCESS").stale()).isTrue();
    }

    private BackupStatusEntryResponse response(Instant at, String outcome) {
        TenantBackupStatus row = org.mockito.Mockito.mock(TenantBackupStatus.class);
        when(row.getTenantId()).thenReturn(UUID.randomUUID());
        when(row.getLastDailyBackupAt()).thenReturn(at);
        when(row.getLastBackupOutcome()).thenReturn(outcome);
        return BackupStatusEntryResponse.from(row, Duration.ofHours(36), NOW);
    }
}
