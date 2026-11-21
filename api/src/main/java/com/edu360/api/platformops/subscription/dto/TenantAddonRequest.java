package com.edu360.api.platformops.subscription.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TenantAddonRequest(
        @NotNull UUID catalogItemId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal quantity,
        @Size(max = 1000) String reason
) { }
