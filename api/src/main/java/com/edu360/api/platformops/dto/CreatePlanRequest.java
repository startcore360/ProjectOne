package com.edu360.api.platformops.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record CreatePlanRequest(
        @NotBlank(message = "Plan name is required")
        @Size(min = 2, max = 100, message = "Plan name must be between 2 and 100 characters")
        String planName,

        String pricingType,

        BigDecimal priceAmount,

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
        BigDecimal storageUnitAmountPerGb
) {
    @AssertTrue(message = "Billing cycle must be 1, 3, 6 or 12 months")
    public boolean isBillingCycleMonthsAllowed() {
        return billingCycleMonths == null || billingCycleMonths == 1
                || billingCycleMonths == 3 || billingCycleMonths == 6 || billingCycleMonths == 12;
    }
}
