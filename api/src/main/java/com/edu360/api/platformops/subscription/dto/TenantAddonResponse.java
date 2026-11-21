package com.edu360.api.platformops.subscription.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.edu360.api.platformops.subscription.TenantSubscriptionAddon;

public record TenantAddonResponse(
        UUID subscriptionAddonId,
        UUID tenantId,
        UUID catalogItemId,
        String itemCode,
        String itemName,
        BigDecimal quantity,
        BigDecimal unitAmount,
        String status,
        UUID invoiceId,
        String requestReason,
        String decisionReason,
        Instant createdAt,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
    public static TenantAddonResponse from(TenantSubscriptionAddon addon, String itemCode, String itemName) {
        return new TenantAddonResponse(addon.getSubscriptionAddonId(), addon.getTenantId(),
                addon.getCatalogItemId(), itemCode, itemName, addon.getQuantity(), addon.getUnitAmount(),
                addon.getStatus(), addon.getInvoiceId(), addon.getRequestReason(), addon.getDecisionReason(), addon.getCreatedAt(),
                addon.getEffectiveFrom(), addon.getEffectiveTo());
    }
}
