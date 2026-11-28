package com.edu360.api.platformops;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import com.edu360.api.platformops.usage.TenantUsageMonthlyRepository;
import com.edu360.api.tenant.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
@RestController
@RequestMapping({"/v1/platform/usage-counts", "/api/platform/usage-counts"})
@RequiredArgsConstructor
@Tag(name="Platform Ops", description="Platform Admin / Product Owner read-mostly operations")
public class UsageCountsController {
    private static final List<String> DAILY = List.of("LOGINS", "ATTENDANCE_MARKED", "FEES_PAID_ONLINE");
    private final PlatformPermissionService permissions;
    private final TenantRepository tenants;
    private final UsageMetricDailyRepository daily;
    private final TenantUsageMonthlyRepository monthly;
    @GetMapping
    @Operation(summary="See built-in usage counts (USAGE_COUNTS_VIEW) - anonymous, combined numbers only")
    public ResponseEntity<UsageCountsResponse> usageCounts(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal))
            throw new UnauthorizedException("USAGE_COUNTS_VIEW requires a platform principal.");
        permissions.authorize(principal, "USAGE_COUNTS_VIEW");
        long total = tenants.count(), optedOut = tenants.countByAnalyticsOptOut(true);
        long included = tenants.countByAnalyticsOptOut(false);
        List<UsageMetricSnapshot> metrics = new ArrayList<>(DAILY.stream()
            .map(code -> daily.findFirstByMetricCodeOrderByUsageDateDesc(code)
                .map(row -> new UsageMetricSnapshot(code, row.getValue(), row.getUsageDate()))
                .orElse(new UsageMetricSnapshot(code, null, null))).toList());
        LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        var active = monthly.sumMeasuredQuantityForAnalyticsTenants(month, "ACTIVE_USERS");
        metrics.add(new UsageMetricSnapshot("ACTIVE_USERS", active == null ? null : active.longValue(), active == null ? null : month));
        return ResponseEntity.ok(new UsageCountsResponse(total, included, optedOut, metrics));
    }
}
