package com.edu360.api.platformops.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.edu360.api.platform.Plan;

public record PlanResponse(
        UUID planId,
        String planName,
        String pricingType,
        BigDecimal priceAmount,
        String currencyCode,
        Integer userLimit,
        Integer storageLimitGb,
        Integer smsLimit,
        Integer apiLimit,
        Integer maxInstitutions,
        boolean customDomainAllowed,
        boolean brandingColorsAllowed,
        Integer billingCycleMonths,
        BigDecimal smsUnitAmount,
        BigDecimal storageUnitAmountPerGb,
        String status,
        Integer rowVersion
) {
    public static PlanResponse from(Plan plan) {
        return new PlanResponse(
                plan.getPlanId(),
                plan.getPlanName(),
                plan.getPricingType(),
                plan.getPriceAmount(),
                plan.getCurrencyCode(),
                plan.getUserLimit(),
                plan.getStorageLimitGb(),
                plan.getSmsLimit(),
                plan.getApiLimit(),
                plan.getMaxInstitutions(),
                plan.isCustomDomainAllowed(),
                plan.isBrandingColorsAllowed(),
                plan.getBillingCycleMonths(),
                plan.getSmsUnitAmount(),
                plan.getStorageUnitAmountPerGb(),
                plan.getStatus(),
                plan.getRowVersion()
        );
    }
}
