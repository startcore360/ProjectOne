package com.edu360.api.platformops.release;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Buffers 5xx counts in memory and persists compact hourly aggregates. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestMetricCollector {
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    private final RequestMetricHourlyRepository repository;
    private final Map<MetricKey, Long> pending = new HashMap<>();

    public void record(String routeTemplate, int statusCode) {
        if (statusCode < 500 || statusCode > 599) {
            return;
        }
        String safeRoute = safeRouteTemplate(routeTemplate);
        MetricKey key = new MetricKey(Instant.now().truncatedTo(ChronoUnit.HOURS), safeRoute);
        synchronized (pending) {
            pending.merge(key, 1L, Long::sum);
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    public void flushHourly() {
        Map<MetricKey, Long> batch;
        synchronized (pending) {
            if (pending.isEmpty()) {
                return;
            }
            batch = new HashMap<>(pending);
            pending.clear();
        }

        for (Map.Entry<MetricKey, Long> entry : batch.entrySet()) {
            MetricKey key = entry.getKey();
            try {
                repository.upsertFiveXx(UUID.randomUUID(), key.bucketStart(), key.routeTemplate(),
                        entry.getValue(), SYSTEM_ACTOR);
            } catch (RuntimeException exception) {
                synchronized (pending) {
                    pending.merge(key, entry.getValue(), Long::sum);
                }
                log.error("Unable to flush request metrics for route {}; counts retained for retry",
                        key.routeTemplate(), exception);
            }
        }
    }

    int pendingCount() {
        synchronized (pending) {
            return pending.values().stream().mapToInt(Long::intValue).sum();
        }
    }

    static String safeRouteTemplate(String routeTemplate) {
        if (routeTemplate == null || routeTemplate.isBlank() || routeTemplate.length() > 300
                || !routeTemplate.startsWith("/")
                || !routeTemplate.matches("[A-Za-z0-9_{}/*:.-]+")) {
            return "/unmatched";
        }
        return routeTemplate;
    }

    private record MetricKey(Instant bucketStart, String routeTemplate) {
    }
}
