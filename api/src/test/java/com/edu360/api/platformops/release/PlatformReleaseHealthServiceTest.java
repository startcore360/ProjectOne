package com.edu360.api.platformops.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.edu360.api.platformops.PlatformAuditOutboxEventRepository;

class PlatformReleaseHealthServiceTest {

    private final PlatformReleaseRepository releases = mock(PlatformReleaseRepository.class);
    private final PlatformAuditOutboxEventRepository audits = mock(PlatformAuditOutboxEventRepository.class);
    private final RequestMetricHourlyRepository metrics = mock(RequestMetricHourlyRepository.class);
    private final PlatformReleaseHealthService service =
            new PlatformReleaseHealthService(releases, audits, metrics);

    @Test
    void returnsBeforeAndAfterFailedAuditAndFiveXxCounts() {
        UUID releaseId = UUID.randomUUID();
        Instant deployedAt = Instant.now().minus(30, ChronoUnit.HOURS);
        PlatformRelease release = PlatformRelease.builder().releaseId(releaseId)
                .versionLabel("v4.3.0").status("DEPLOYED").deployedAt(deployedAt).build();
        when(releases.findById(releaseId)).thenReturn(Optional.of(release));
        when(metrics.count()).thenReturn(1L);
        when(audits.countByOutcomeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                org.mockito.ArgumentMatchers.eq("FAILED"), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(deployedAt))).thenReturn(3L);
        when(audits.countByOutcomeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                org.mockito.ArgumentMatchers.eq("FAILED"), org.mockito.ArgumentMatchers.eq(deployedAt),
                org.mockito.ArgumentMatchers.any())).thenReturn(7L);
        when(metrics.sumFiveXxBetween(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(deployedAt)))
                .thenReturn(2L);
        when(metrics.sumFiveXxBetween(org.mockito.ArgumentMatchers.eq(deployedAt), org.mockito.ArgumentMatchers.any()))
                .thenReturn(5L);

        ReleaseHealthResponse response = service.getHealth(releaseId);

        assertThat(response.before().failedAuditEvents()).isEqualTo(3);
        assertThat(response.before().http5xxRequests()).isEqualTo(2L);
        assertThat(response.after().failedAuditEvents()).isEqualTo(7);
        assertThat(response.after().http5xxRequests()).isEqualTo(5L);
        assertThat(response.after().complete()).isTrue();
    }

    @Test
    void returnsNullFiveXxValuesWhenRequestMetricTableHasNoData() {
        UUID releaseId = UUID.randomUUID();
        Instant deployedAt = Instant.now().minus(2, ChronoUnit.DAYS);
        PlatformRelease release = PlatformRelease.builder().releaseId(releaseId)
                .versionLabel("v4.3.1").status("DEPLOYED").deployedAt(deployedAt).build();
        when(releases.findById(releaseId)).thenReturn(Optional.of(release));
        when(metrics.count()).thenReturn(0L);
        when(audits.countByOutcomeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0L);

        ReleaseHealthResponse response = service.getHealth(releaseId);

        assertThat(response.before().http5xxRequests()).isNull();
        assertThat(response.after().http5xxRequests()).isNull();
    }

    @Test
    void rejectsHealthRequestForReleaseNotDeployed() {
        UUID releaseId = UUID.randomUUID();
        when(releases.findById(releaseId)).thenReturn(Optional.of(
                PlatformRelease.builder().releaseId(releaseId).status("STAGING_TESTED").build()));

        assertThatThrownBy(() -> service.getHealth(releaseId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("RELEASE_NOT_DEPLOYED");
    }
}
