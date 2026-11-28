package com.edu360.api.platformops.dto;

import java.util.UUID;

import com.edu360.api.platform.Plan;
import com.edu360.api.tenant.Tenant;

/**
 * Tenant settings, plus the plan's own limits (E1-S3) so the screen can show "plan limit" beside
 * each field and explain why a value above it needs the Product Owner. The plan fields are null
 * when the tenant has no plan.
 */
public record TenantSettingsResponse(
        UUID tenantId,
        String tenantName,
        String planName,
        String state,
        String timeZone,
        String dataRegion,
        boolean analyticsOptOut,
        String customDomain,
        Integer userLimit,
        Integer storageLimitGb,
        Integer smsLimit,
        Integer apiLimit,
        Integer planUserLimit,
        Integer planStorageLimitGb,
        Integer planSmsLimit,
        Integer planApiLimit,
        boolean customDomainAllowed,
        boolean brandingColorsAllowed
) {
    public static TenantSettingsResponse from(Tenant tenant, String planName) {
        return from(tenant, planName, null);
    }

    public static TenantSettingsResponse from(Tenant tenant, String planName, Plan plan) {
        return new TenantSettingsResponse(
                tenant.getTenantId(),
                tenant.getTenantName(),
                planName,
                tenant.getState() != null ? tenant.getState().name() : null,
                tenant.getTimeZone(),
                tenant.getDataRegion(),
                tenant.isAnalyticsOptOut(),
                tenant.getCustomDomain(),
                tenant.getUserLimit(),
                tenant.getStorageLimitGb(),
                tenant.getSmsLimit(),
                tenant.getApiLimit(),
                plan == null ? null : plan.getUserLimit(),
                plan == null ? null : plan.getStorageLimitGb(),
                plan == null ? null : plan.getSmsLimit(),
                plan == null ? null : plan.getApiLimit(),
                plan != null && plan.isCustomDomainAllowed(),
                plan != null && plan.isBrandingColorsAllowed()
        );
    }
}
