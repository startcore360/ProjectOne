package com.edu360.api.platformops.subscription.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PlanComponentResponse(
        UUID planComponentId,
        UUID catalogItemId,
        String itemCode,
        String itemName,
        String category,
        String unitCode,
        String pricingBasis,
        BigDecimal includedQuantity,
        BigDecimal unitAmount,
        boolean enabled
) { }
