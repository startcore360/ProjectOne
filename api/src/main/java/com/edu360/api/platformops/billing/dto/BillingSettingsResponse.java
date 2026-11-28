package com.edu360.api.platformops.billing.dto;
public record BillingSettingsResponse(Integer invoiceDueDays) {
 public static BillingSettingsResponse fromEntity(com.edu360.api.platformops.billing.BillingSettings settings) {
  return new BillingSettingsResponse(settings.getInvoiceDueDays());
 }
}
