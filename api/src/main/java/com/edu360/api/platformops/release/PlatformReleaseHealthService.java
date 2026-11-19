package com.edu360.api.platformops.release;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.edu360.api.platformops.PlatformAuditOutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlatformReleaseHealthService {
    private static final Duration WINDOW = Duration.ofHours(24);

    private final PlatformReleaseRepository releaseRepository;
    private final PlatformAuditOutboxEventRepository auditRepository;
    private final RequestMetricHourlyRepository requestMetricRepository;

    @Transactional(readOnly = true, transactionManager = "platformTransactionManager")
    public ReleaseHealthResponse getHealth(UUID releaseId) {
        PlatformRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RELEASE_NOT_FOUND"));
        if (!"DEPLOYED".equals(release.getStatus()) || release.getDeployedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RELEASE_NOT_DEPLOYED");
        }

        Instant deployedAt = release.getDeployedAt();
        Instant beforeFrom = deployedAt.minus(WINDOW);
        Instant afterEnd = deployedAt.plus(WINDOW);
        Instant measuredAfterEnd = Instant.now().isBefore(afterEnd) ? Instant.now() : afterEnd;
        boolean afterComplete = !Instant.now().isBefore(afterEnd);
        boolean requestMetricsMeasured = requestMetricRepository.count() > 0;

        ReleaseHealthResponse.Window before = new ReleaseHealthResponse.Window(
                beforeFrom,
                deployedAt,
                auditRepository.countByOutcomeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                        "FAILED", beforeFrom, deployedAt),
                requestMetricsMeasured ? requestMetricRepository.sumFiveXxBetween(beforeFrom, deployedAt) : null,
                true);

        ReleaseHealthResponse.Window after = new ReleaseHealthResponse.Window(
                deployedAt,
                measuredAfterEnd,
                auditRepository.countByOutcomeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                        "FAILED", deployedAt, measuredAfterEnd),
                requestMetricsMeasured
                        ? requestMetricRepository.sumFiveXxBetween(deployedAt, measuredAfterEnd)
                        : null,
                afterComplete);

        return new ReleaseHealthResponse(release.getReleaseId(), release.getVersionLabel(),
                deployedAt, before, after);
    }
}
