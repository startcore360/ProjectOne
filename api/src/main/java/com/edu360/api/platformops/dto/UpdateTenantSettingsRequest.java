package com.edu360.api.platformops.dto;

public record UpdateTenantSettingsRequest(
        String timeZone,
        String dataRegion,
        Boolean analyticsOptOut,
        String customDomain,
        Integer userLimit,
        Integer storageLimitGb,
        Integer smsLimit,
        Integer apiLimit
) {}
