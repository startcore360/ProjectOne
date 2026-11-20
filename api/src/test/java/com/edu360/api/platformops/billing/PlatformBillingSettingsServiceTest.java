package com.edu360.api.platformops.billing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platformops.billing.dto.UpdateBillingSettingsRequest;
class PlatformBillingSettingsServiceTest {
 private BillingSettingsRepository repository; private PlatformAuditWriter auditWriter; private PlatformBillingSettingsService service; private PlatformPrincipal actor;
 @BeforeEach void setUp() {
  repository=mock(BillingSettingsRepository.class); auditWriter=mock(PlatformAuditWriter.class); service=new PlatformBillingSettingsService(repository,auditWriter);
  actor=new PlatformPrincipal(UUID.randomUUID(),PlatformRole.BILLING_MANAGER,"billing@edu360.local",false);
 }
 @Test void ACT_BILLING_SETTINGS_ALLOW_updatesDueDaysAndAuditsChange() {
  BillingSettings settings=BillingSettings.builder().billingSettingsId(1).invoiceDueDays(15).createdAt(Instant.now()).createdBy(actor.platformUserId()).updatedAt(Instant.now()).updatedBy(actor.platformUserId()).rowVersion(1).build();
  when(repository.findById(1)).thenReturn(Optional.of(settings)); when(repository.save(any(BillingSettings.class))).thenAnswer(inv->inv.getArgument(0));
  var result=service.updateSettings(new UpdateBillingSettingsRequest(30),actor);
  assertThat(result.invoiceDueDays()).isEqualTo(30); verify(auditWriter).record(any(),any(),any(),any(),any(),any(),any(),any(),any());
 }
 @Test void ACT_BILLING_SETTINGS_GUARD_dueDaysOutsideRangeRejected() {
  assertThatThrownBy(()->service.updateSettings(new UpdateBillingSettingsRequest(0),actor)).isInstanceOf(com.edu360.api.exception.BusinessRuleViolationException.class).hasMessageContaining("between 1 and 365");
 }
 @Test void ACT_BILLING_SETTINGS_ALLOW_readsSeededDefault15() {
  when(repository.findById(1)).thenReturn(Optional.of(BillingSettings.builder().billingSettingsId(1).invoiceDueDays(15).build()));
  assertThat(service.getSettings().invoiceDueDays()).isEqualTo(15);
 }
}
