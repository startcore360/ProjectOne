package com.edu360.api.platformops.dto;

import java.util.UUID;

import com.edu360.api.tenant.Tenant;

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
        Integer apiLimit
) {
    public static TenantSettingsResponse from(Tenant tenant, String planName) {
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
                tenant.getApiLimit()
        );
    }
}
