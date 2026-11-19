package com.edu360.api.platformops.usage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.PlatformPermissionService;
import lombok.RequiredArgsConstructor;
@RestController
@RequestMapping({"/v1/platform/tenants/{tenantId}/usage", "/api/platform/tenants/{tenantId}/usage"})
@RequiredArgsConstructor
public class TenantUsageController {
    private final TenantUsageMonthlyRepository usage;
    private final com.edu360.api.tenant.TenantRepository tenants;
    private final PlatformPermissionService permissions;
    public record TenantUsageMonthlyResponse(UUID tenantUsageMonthlyId, UUID tenantId, LocalDate usageMonth,
        String metricCode, BigDecimal quantity, boolean measured, Instant computedAt, boolean isFinal) { }
    @GetMapping
    public ResponseEntity<List<TenantUsageMonthlyResponse>> getTenantUsage(@PathVariable UUID tenantId, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof PlatformPrincipal principal))
            throw new UnauthorizedException("Platform credentials required.");
        permissions.authorize(principal, "USAGE_COUNTS_VIEW");
        var tenant = tenants.findById(tenantId).orElseThrow(() ->
            new com.edu360.api.exception.ResourceNotFoundException("Tenant not found: " + tenantId));
        // Preserve the existing tenant-analytics opt-out: no tenant usage is exposed in this view.
        if (tenant.isAnalyticsOptOut()) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(usage.findByTenantIdOrderByUsageMonthDesc(tenantId).stream()
            .map(r -> new TenantUsageMonthlyResponse(r.getTenantUsageMonthlyId(), r.getTenantId(), r.getUsageMonth(),
                r.getMetricCode(), r.getQuantity(), r.isMeasured(), r.getComputedAt(), r.isFinal())).toList());
    }
}
