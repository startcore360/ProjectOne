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
import com.edu360.api.platformops.PlatformPermissionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform/tenants/{tenantId}/usage", "/api/platform/tenants/{tenantId}/usage"})
@RequiredArgsConstructor
public class TenantUsageController {

    private final TenantUsageMonthlyRepository usageRepository;
    private final com.edu360.api.tenant.TenantRepository tenantRepository;
    private final PlatformPermissionService permissionService;

    public record TenantUsageMonthlyResponse(
            UUID tenantUsageMonthlyId,
            UUID tenantId,
            LocalDate usageMonth,
            String metricCode,
            BigDecimal quantity,
            Instant computedAt,
            boolean isFinal
    ) {}

    @GetMapping
    public ResponseEntity<List<TenantUsageMonthlyResponse>> getTenantUsage(
            @PathVariable UUID tenantId,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "USAGE_COUNTS_VIEW");

        // L-06: an analytics opt-out means this tenant's usage is not reported at all (404, as for an unknown tenant).
        com.edu360.api.tenant.Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new com.edu360.api.exception.ResourceNotFoundException("Tenant not found: " + tenantId));
        if (tenant.isAnalyticsOptOut()) {
            return ResponseEntity.ok(List.of());
        }

        List<TenantUsageMonthly> records = usageRepository.findByTenantIdOrderByUsageMonthDesc(tenantId);
        return ResponseEntity.ok(records.stream()
                .map(r -> new TenantUsageMonthlyResponse(
                        r.getTenantUsageMonthlyId(),
                        r.getTenantId(),
                        r.getUsageMonth(),
                        r.getMetricCode(),
                        r.getQuantity(),
                        r.getComputedAt(),
                        r.isFinal()
                ))
                .toList());
    }
}
