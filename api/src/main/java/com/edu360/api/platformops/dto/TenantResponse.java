package com.edu360.api.platformops.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantState;

public record TenantResponse(
        UUID tenantId,
        String tenantName,
        String tenantType,
        UUID planId,
        String planName,
        TenantState state,
        IsolationMode isolationMode,
        String subdomain,
        String customDomain,
        String timeZone,
        String dataRegion,
        Instant trialEndsAt,
        Integer userLimit,
        Integer storageLimitGb,
        Integer smsLimit,
        Integer apiLimit,
        String keycloakRealm,
        String firstAdminEmail,
        String firstAdminMobile,
        String firstAdminNationalIdLast4,
        Instant createdAt,
        String firstAdminTemporaryPassword,
        UUID firstAdminUserId
) {
    public static TenantResponse from(Tenant tenant, String planName, String adminEmail, String adminMobile, String nationalIdLast4) {
        return from(tenant, planName, adminEmail, adminMobile, nationalIdLast4, null, null);
    }

    public static TenantResponse from(
            Tenant tenant,
            String planName,
            String adminEmail,
            String adminMobile,
            String nationalIdLast4,
            String firstAdminTemporaryPassword,
            UUID firstAdminUserId
    ) {
        return new TenantResponse(
                tenant.getTenantId(),
                tenant.getTenantName(),
                tenant.getTenantType(),
                tenant.getPlanId(),
                planName,
                tenant.getState(),
                tenant.getIsolationMode(),
                tenant.getSubdomain(),
                tenant.getCustomDomain(),
                tenant.getTimeZone(),
                tenant.getDataRegion(),
                tenant.getTrialEndsAt(),
                tenant.getUserLimit(),
                tenant.getStorageLimitGb(),
                tenant.getSmsLimit(),
                tenant.getApiLimit(),
                tenant.getKeycloakRealm(),
                adminEmail,
                adminMobile,
                nationalIdLast4,
                tenant.getCreatedAt(),
                firstAdminTemporaryPassword,
                firstAdminUserId
        );
    }
}
