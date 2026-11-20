package com.edu360.api.platformops.dto;
import java.time.LocalDate;
import java.util.UUID;
import com.edu360.api.platformops.billing.dto.InvoiceResponse;
public record TenantPlanChangeResponse(UUID tenantId,UUID previousPlanId,UUID activePlanId,UUID pendingPlanId,
 PlanChangeEffective effective,LocalDate nextRenewalAt,InvoiceResponse prorationInvoice) {}
