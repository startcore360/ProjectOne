package com.edu360.api.platformops;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
class PlatformTenantRenewalJobTest {
 @Test void ACT_RENEWAL_JOB_ALLOW_runsDailyRenewalService() {
  PlatformTenantRenewalService service=mock(PlatformTenantRenewalService.class);
  new PlatformTenantRenewalJob(service).scheduledDailyRenewal();
  verify(service).processDueRenewals(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));
 }
}
