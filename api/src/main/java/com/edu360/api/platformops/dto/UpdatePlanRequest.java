package com.edu360.api.platformops.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;

public record UpdatePlanRequest(
        @Size(min = 2, max = 100, message = "Plan name must be between 2 and 100 characters")
        String planName,

        BigDecimal priceAmount,

        String pricingType,

        String currencyCode,

        Integer userLimit,

        Integer storageLimitGb,

        Integer smsLimit,

        Integer apiLimit,

        Integer maxInstitutions,

        Boolean customDomainAllowed,

        Boolean brandingColorsAllowed,

        @Min(value = 1, message = "Billing cycle must be 1, 3, 6 or 12 months")
        @Max(value = 12, message = "Billing cycle must be 1, 3, 6 or 12 months")
        Integer billingCycleMonths,

        @DecimalMin(value = "0.00", message = "SMS unit amount cannot be negative")
        BigDecimal smsUnitAmount,

        @DecimalMin(value = "0.00", message = "Storage unit amount cannot be negative")
        BigDecimal storageUnitAmountPerGb,

        String status
) {}
