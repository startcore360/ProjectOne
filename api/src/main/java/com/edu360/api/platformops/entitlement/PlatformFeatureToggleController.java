package com.edu360.api.platformops.entitlement;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.role.PlatformAuditWriter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform", "/api/platform"})
@Tag(name = "Platform Entitlements", description = "Plan and tenant module feature entitlements")
public class PlatformFeatureToggleController {

    private final PlatformPermissionService permissionService;
    private final PlanEntitlementRepository planEntitlementRepository;
    private final TenantEntitlementRepository tenantEntitlementRepository;
    private final PlatformAuditWriter auditWriter;

    @org.springframework.beans.factory.annotation.Autowired
    public PlatformFeatureToggleController(
            PlatformPermissionService permissionService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PlanEntitlementRepository planEntitlementRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TenantEntitlementRepository tenantEntitlementRepository,
            PlatformAuditWriter auditWriter
    ) {
        this.permissionService = permissionService;
        this.planEntitlementRepository = planEntitlementRepository;
        this.tenantEntitlementRepository = tenantEntitlementRepository;
        this.auditWriter = auditWriter;
    }

    public record EntitlementsUpdateRequest(
            Map<String, Boolean> features
    ) {}

    public record EntitlementsResponse(
            UUID targetId,
            String targetType,
            Map<String, Boolean> features
    ) {}

    @PutMapping("/plans/{id}/entitlements")
    @Operation(summary = "Update subscription plan module entitlements (FEATURE_TOGGLE)")
    public ResponseEntity<EntitlementsResponse> updatePlanEntitlements(
            @PathVariable UUID id,
            @RequestBody EntitlementsUpdateRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorize(principal, "FEATURE_TOGGLE");

        if (req.features() != null && planEntitlementRepository != null) {
            Instant now = Instant.now();
            for (Map.Entry<String, Boolean> entry : req.features().entrySet()) {
                String featureKey = entry.getKey();
                boolean enabled = Boolean.TRUE.equals(entry.getValue());
                String[] parts = featureKey.split(":", 2);
                String moduleCode = parts[0];
                String featureCode = parts.length > 1 ? parts[1] : null;

                PlanEntitlement entitlement = planEntitlementRepository
                        .findByPlanIdAndModuleCodeAndFeatureCode(id, moduleCode, featureCode)
                        .orElseGet(() -> PlanEntitlement.builder()
                                .planEntitlementId(UUID.randomUUID())
                                .planId(id)
                                .moduleCode(moduleCode)
                                .featureCode(featureCode)
                                .createdAt(now)
                                .createdBy(principal.platformUserId())
                                .rowVersion(1)
                                .build());

                entitlement.setEnabled(enabled);
                entitlement.setUpdatedAt(now);
                entitlement.setUpdatedBy(principal.platformUserId());
                planEntitlementRepository.save(entitlement);
            }
        }

        auditWriter.record(principal, "CONFIG_CHANGE", "FEATURE_TOGGLE", "PLAN", id);

        return ResponseEntity.ok(new EntitlementsResponse(id, "PLAN", req.features()));
    }

    @PutMapping("/tenants/{id}/entitlements")
    @Operation(summary = "Update tenant override feature entitlements (FEATURE_TOGGLE)")
    public ResponseEntity<EntitlementsResponse> updateTenantEntitlements(
            @PathVariable UUID id,
            @RequestBody EntitlementsUpdateRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorize(principal, "FEATURE_TOGGLE");

        if (req.features() != null && tenantEntitlementRepository != null) {
            Instant now = Instant.now();
            for (Map.Entry<String, Boolean> entry : req.features().entrySet()) {
                String featureKey = entry.getKey();
                boolean enabled = Boolean.TRUE.equals(entry.getValue());
                String[] parts = featureKey.split(":", 2);
                String moduleCode = parts[0];
                String featureCode = parts.length > 1 ? parts[1] : null;

                TenantEntitlement entitlement = tenantEntitlementRepository
                        .findByTenantIdAndModuleCodeAndFeatureCode(id, moduleCode, featureCode)
                        .orElseGet(() -> TenantEntitlement.builder()
                                .tenantEntitlementId(UUID.randomUUID())
                                .tenantId(id)
                                .moduleCode(moduleCode)
                                .featureCode(featureCode)
                                .createdAt(now)
                                .createdBy(principal.platformUserId())
                                .rowVersion(1)
                                .build());

                entitlement.setEnabled(enabled);
                entitlement.setUpdatedAt(now);
                entitlement.setUpdatedBy(principal.platformUserId());
                tenantEntitlementRepository.save(entitlement);
            }
        }

        auditWriter.record(principal, "CONFIG_CHANGE", "FEATURE_TOGGLE", "TENANT", id);

        return ResponseEntity.ok(new EntitlementsResponse(id, "TENANT", req.features()));
    }

    private PlatformPrincipal requirePlatformPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("Platform credentials required; no platform principal on this request.");
        }
        return principal;
    }
}
