package com.edu360.api.platformops.billing;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.platformops.billing.dto.BillingSettingsResponse;
import com.edu360.api.platformops.billing.dto.UpdateBillingSettingsRequest;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class PlatformBillingSettingsService {
 private final BillingSettingsRepository repository; private final PlatformAuditWriter auditWriter;
 @Transactional(readOnly=true) public BillingSettingsResponse getSettings() {
  BillingSettings settings=repository.findById(1).orElseThrow(()->new ResourceNotFoundException("Billing settings are not initialized."));
  return BillingSettingsResponse.fromEntity(settings);
 }
 @Transactional public BillingSettingsResponse updateSettings(UpdateBillingSettingsRequest request,PlatformPrincipal actor) {
  if(request.invoiceDueDays()==null||request.invoiceDueDays()<1||request.invoiceDueDays()>365)
   throw new BusinessRuleViolationException("INVALID_INVOICE_DUE_DAYS","Invoice due days must be between 1 and 365.");
  BillingSettings settings=repository.findById(1).orElseThrow(()->new ResourceNotFoundException("Billing settings are not initialized."));
  int previous=settings.getInvoiceDueDays(); settings.setInvoiceDueDays(request.invoiceDueDays());
  settings.setUpdatedAt(Instant.now()); settings.setUpdatedBy(actor.platformUserId());
  BillingSettings saved=repository.save(settings);
  auditWriter.record(actor,null,"BILLING_SETTINGS_UPDATED","CONFIG_CHANGE","INVOICE_MANAGE","BILLING_SETTINGS",
   UUID.nameUUIDFromBytes("EDU360_BILLING_SETTINGS".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
   PlatformAuditWriter.SUCCESS,Map.of("invoice_due_days",Map.of("old",previous,"new",request.invoiceDueDays())));
  return BillingSettingsResponse.fromEntity(saved);
 }
}
