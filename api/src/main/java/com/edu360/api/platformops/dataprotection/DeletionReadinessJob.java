package com.edu360.api.platformops.dataprotection;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * E5-S2 daily deletion readiness sweep (AP-23). The work and its rules live in
 * {@link PlatformDataProtectionService#advanceDeletionReadiness(LocalDate)} so tests can call it with a fixed date.
 */
@Component
@RequiredArgsConstructor
public class DeletionReadinessJob {

    private static final Logger log = LoggerFactory.getLogger(DeletionReadinessJob.class);

    private final PlatformDataProtectionService dataProtectionService;

    @Scheduled(cron = "0 15 2 * * *", zone = "UTC")
    public void runDaily() {
        int moved = dataProtectionService.advanceDeletionReadiness(LocalDate.now(ZoneOffset.UTC));
        if (moved > 0) {
            log.info("Deletion readiness: {} request status change(s)", moved);
        }
    }
}
