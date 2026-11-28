package com.edu360.api.platformops.subscription.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommercialCatalogItemRequest(
        @NotBlank @Size(min = 2, max = 80) String itemCode,
        @NotBlank @Size(min = 2, max = 120) String itemName,
        @NotBlank String category,
        @NotBlank String unitCode,
        @NotBlank String pricingBasis,
        @NotNull @DecimalMin("0.00") BigDecimal unitAmount,
        @NotNull @Min(1) Integer unitsPerQuantity,
        @Min(1) @Max(36) Integer billingIntervalMonths,
        String moduleCode,
        @Size(max = 1000) String description,
        Boolean available
) { }
