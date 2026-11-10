package com.edu360.api.platformops.dto;

import java.util.List;

/**
 * Response for GET /api/platform/usage-counts (USAGE_COUNTS_VIEW). Only anonymous, combined
 * numbers: a platform-wide tenant count (platform.tenant, nothing per-tenant-identifiable beyond
 * what TenantRepository already exposes) and the latest platform.usage_metric_daily snapshot per
 * metric. {@code totalUserCountGap} documents a real, deliberate omission rather than a
 * fabricated figure - see the controller javadoc.
 */
public record UsageCountsResponse(
        long totalTenantCount,
        long tenantsInAnalytics,
        long tenantsOptedOut,
        List<UsageMetricSnapshot> metrics,
        String totalUserCountGap) {
}
