package com.edu360.api.platformops;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.dto.UsageCountsResponse;
import com.edu360.api.platformops.dto.UsageMetricSnapshot;
import com.edu360.api.tenant.TenantRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * USAGE_COUNTS_VIEW - "See built-in usage counts." Who: Product Owner, Platform Admin
 * (sql/05_ref_data.sql ids 1686/1687). Rule from the brief: anonymous combined numbers only,
 * nothing per-tenant-identifiable beyond what's already public via platform.tenant.
 *
 * Two real data sources, both platform-schema, neither tenant-RLS-scoped:
 *  - {@link TenantRepository} (read-only use, as instructed) for the total tenant count -
 *    platform.tenant has no RLS of its own (Tenant's own javadoc: it IS the tenant directory).
 *  - {@link UsageMetricDailyRepository} for platform.usage_metric_daily, the table whose own
 *    schema comment literally says "Anonymous combined platform usage counts ... opted-out
 *    tenants excluded" - i.e. built for exactly this story.
 *
 * REAL GAP, flagged rather than invented past: the brief also asks for "total user count across
 * the platform." No table in sql/01_schema.sql materializes that number. Computing it for real
 * would mean summing app.user_account rows across every tenant's own RLS-scoped schema/database
 * from a platform-only request - precisely the cross-tenant reach PlatformPrincipal's own class
 * comment says a platform login must never get, implicit or otherwise. So this endpoint reports
 * the tenant count and the metrics that ARE anonymized/materialized, and returns
 * totalUserCountGap explaining the omission instead of querying across tenants or fabricating a
 * number.
 */
@RestController
@RequestMapping({"/v1/platform/usage-counts", "/api/platform/usage-counts"})
@RequiredArgsConstructor
@Tag(name = "Platform Ops", description = "Platform Admin / Product Owner read-mostly operations")
public class UsageCountsController {

    private static final List<String> METRIC_CODES = List.of("LOGINS", "ATTENDANCE_MARKED", "FEES_PAID_ONLINE");

    private final PlatformPermissionService platformPermissionService;
    private final TenantRepository tenantRepository;
    private final UsageMetricDailyRepository usageMetricDailyRepository;

    @GetMapping
    @Operation(summary = "See built-in usage counts (USAGE_COUNTS_VIEW) - anonymous, combined numbers only")
    public ResponseEntity<UsageCountsResponse> usageCounts(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("USAGE_COUNTS_VIEW is a platform-side action; no platform principal on this request.");
        }
        platformPermissionService.authorize(principal, "USAGE_COUNTS_VIEW");

        long totalTenantCount = tenantRepository.count();
        // L-06 / R2-13 / PV-07: a tenant that opted out of analytics is counted in the directory total but
        // never in the analytics numbers, and the response says how many were left out.
        long tenantsOptedOut = tenantRepository.countByAnalyticsOptOut(true);
        long tenantsInAnalytics = tenantRepository.countByAnalyticsOptOut(false);

        List<UsageMetricSnapshot> metrics = METRIC_CODES.stream()
                .map(code -> usageMetricDailyRepository.findFirstByMetricCodeOrderByUsageDateDesc(code)
                        .map(row -> new UsageMetricSnapshot(code, row.getValue(), row.getUsageDate()))
                        .orElse(new UsageMetricSnapshot(code, null, null)))
                .toList();

        String totalUserCountGap = "Not available: no table in the committed sql/ bundle materializes a "
                + "platform-wide user count, and computing one by querying every tenant's own "
                + "RLS-scoped data from a platform request would break tenant isolation by design. "
                + "Flagged as an open gap rather than estimated.";

        return ResponseEntity.ok(new UsageCountsResponse(totalTenantCount, tenantsInAnalytics, tenantsOptedOut, metrics, totalUserCountGap));
    }
}
