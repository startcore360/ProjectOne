package com.edu360.api.platformops.billing.dto;
import java.util.List;
import java.util.UUID;
public record BillingCycleResponse(List<InvoiceResponse> created, List<SkippedTenant> skipped) {
 public record SkippedTenant(UUID tenantId, String reason) {}
}
