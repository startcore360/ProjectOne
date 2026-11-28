package com.edu360.api.platformops.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class RequestMetricCollectorTest {

    private final RequestMetricHourlyRepository repository = org.mockito.Mockito.mock(RequestMetricHourlyRepository.class);
    private final RequestMetricCollector collector = new RequestMetricCollector(repository);

    @Test
    void recordsOnlyFiveXxAgainstSafeRouteTemplate() {
        collector.record("/api/releases/{id}", 503);
        collector.record("/api/releases/{id}", 404);
        collector.record("/api/releases/actual-id", 502);

        assertThat(collector.pendingCount()).isEqualTo(2);
        assertThat(RequestMetricCollector.safeRouteTemplate("/api/releases/{id}"))
                .isEqualTo("/api/releases/{id}");
        assertThat(RequestMetricCollector.safeRouteTemplate("/api/releases/actual-id"))
                .isEqualTo("/unmatched");
    }

    @Test
    void flushesCountsToHourlyRepository() {
        collector.record("/api/releases/{id}", 503);
        collector.record("/api/releases/{id}", 502);

        collector.flushHourly();

        verify(repository).upsertFiveXx(org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("/api/releases/{id}"),
                org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.any(UUID.class));
        assertThat(collector.pendingCount()).isZero();
    }

    @Test
    void retainsCountsWhenFlushFailsForRetry() {
        collector.record("/api/releases/{id}", 500);
        when(repository.upsertFiveXx(org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(UUID.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        collector.flushHourly();

        assertThat(collector.pendingCount()).isEqualTo(1);
    }
}
