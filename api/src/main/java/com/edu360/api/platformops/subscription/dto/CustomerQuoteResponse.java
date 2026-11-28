package com.edu360.api.platformops.subscription.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CustomerQuoteResponse(
        String currencyCode,
        int billingCycleMonths,
        List<PlanQuote> plans,
        UUID lowestCostPlanId,
        UUID bestFitPlanId
) {
    public record PlanQuote(
            UUID planId,
            String planName,
            String pricingType,
            boolean eligible,
            String recommendation,
            List<String> fitReasons,
            List<String> unmetRequirements,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal total,
            BigDecimal tax,
            List<QuoteLine> lines
    ) { }

    public record QuoteLine(String code, String description, BigDecimal quantity,
            BigDecimal unitAmount, BigDecimal amount) { }
}
