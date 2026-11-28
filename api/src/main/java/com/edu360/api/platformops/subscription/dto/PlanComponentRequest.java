package com.edu360.api.platformops.subscription.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PlanComponentRequest(
        @NotNull UUID catalogItemId,
        @NotNull @DecimalMin("0.00") BigDecimal includedQuantity,
        @DecimalMin("0.00") BigDecimal unitAmountOverride,
        Boolean enabled
) { }
