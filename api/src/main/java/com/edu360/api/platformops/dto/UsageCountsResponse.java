package com.edu360.api.platformops.dto;
import java.util.List;
/** Anonymous combined platform usage counts; no tenant/user-level figures are returned. */
public record UsageCountsResponse(long totalTenantCount, long tenantsInAnalytics, long tenantsOptedOut,
        List<UsageMetricSnapshot> metrics) { }
