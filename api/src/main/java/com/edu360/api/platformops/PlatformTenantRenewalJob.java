package com.edu360.api.platformops;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PlatformTenantRenewalJob {
 private static final ZoneId BILLING_ZONE=ZoneId.of("Asia/Kolkata");
 private final PlatformTenantRenewalService renewalService;
 @Scheduled(cron="0 30 23 * * *",zone="Asia/Kolkata")
 public void scheduledDailyRenewal() { renewalService.processDueRenewals(LocalDate.now(BILLING_ZONE)); }
}
