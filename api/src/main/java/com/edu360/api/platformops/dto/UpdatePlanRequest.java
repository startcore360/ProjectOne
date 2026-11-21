package com.edu360.api.platformops.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;

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

        String status,

        @Min(value = 1, message = "Maximum colleges must be positive")
        Integer maxColleges,

        @Min(value = 1, message = "Maximum schools must be positive")
        Integer maxSchools,

        @Min(value = 1, message = "Maximum students must be positive")
        Integer maxStudents,

        @Min(value = 1, message = "Maximum staff must be positive")
        Integer maxStaff,

        @DecimalMin(value = "0.00", message = "Setup fee cannot be negative")
        BigDecimal setupFeeAmount,

        String discountType,

        @DecimalMin(value = "0.00", message = "Discount value cannot be negative")
        BigDecimal discountValue,

        @Min(value = 1, message = "Contract duration must be between 1 and 360 months")
        @Max(value = 360, message = "Contract duration must be between 1 and 360 months")
        Integer contractDurationMonths,

        @Min(value = 0, message = "Grace period must be between 0 and 365 days")
        @Max(value = 365, message = "Grace period must be between 0 and 365 days")
        Integer gracePeriodDays,

        String renewalMode
) {
    public UpdatePlanRequest(
            String planName, BigDecimal priceAmount, String pricingType, String currencyCode,
            Integer userLimit, Integer storageLimitGb, Integer smsLimit, Integer apiLimit,
            Integer maxInstitutions, Boolean customDomainAllowed, Boolean brandingColorsAllowed,
            Integer billingCycleMonths, BigDecimal smsUnitAmount, BigDecimal storageUnitAmountPerGb,
            String status) {
        this(planName, priceAmount, pricingType, currencyCode, userLimit, storageLimitGb, smsLimit,
                apiLimit, maxInstitutions, customDomainAllowed, brandingColorsAllowed, billingCycleMonths,
                smsUnitAmount, storageUnitAmountPerGb, status, null, null, null, null, null, null,
                null, null, null, null);
    }

    @AssertTrue(message = "Billing cycle must be between 1 and 36 months")
    public boolean isBillingCycleMonthsAllowed() {
        return billingCycleMonths == null || (billingCycleMonths >= 1 && billingCycleMonths <= 36);
    }

    @AssertTrue(message = "Discount type and value must be provided together")
    public boolean isDiscountConfigurationValid() {
        if ("NONE".equals(discountType)) {
            return discountValue == null;
        }
        if (discountType == null || discountValue == null) {
            return discountType == null && discountValue == null;
        }
        return ("PERCENT".equals(discountType) || "FIXED".equals(discountType))
                && (!"PERCENT".equals(discountType) || discountValue.compareTo(new BigDecimal("100")) <= 0);
    }
}
