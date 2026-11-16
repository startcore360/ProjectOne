package com.edu360.api.platformops.entitlement;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.exception.ResourceNotFoundException;

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
    private final TenantRepository tenantRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public PlatformFeatureToggleController(
            PlatformPermissionService permissionService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PlanEntitlementRepository planEntitlementRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TenantEntitlementRepository tenantEntitlementRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TenantRepository tenantRepository,
            PlatformAuditWriter auditWriter
    ) {
        this.permissionService = permissionService;
        this.planEntitlementRepository = planEntitlementRepository;
        this.tenantEntitlementRepository = tenantEntitlementRepository;
        this.auditWriter = auditWriter;
        this.tenantRepository = tenantRepository;
    }

    public record EntitlementsUpdateRequest(
            Map<String, Boolean> features
    ) {}

    public record EntitlementsResponse(
            UUID targetId,
            String targetType,
            Map<String, Boolean> features
    ) {}

    @GetMapping("/plans/{id}/entitlements")
    @Operation(summary = "Read subscription plan module entitlements (FEATURE_TOGGLE)")
    public ResponseEntity<EntitlementsResponse> getPlanEntitlements(
            @PathVariable UUID id,
            Authentication auth
    ) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorize(principal, "FEATURE_TOGGLE");
        Map<String, Boolean> features = new LinkedHashMap<>();
        if (planEntitlementRepository != null) {
            planEntitlementRepository.findByPlanId(id).forEach(entitlement ->
                    features.put(featureKey(entitlement.getModuleCode(), entitlement.getFeatureCode()), entitlement.isEnabled()));
        }
        return ResponseEntity.ok(new EntitlementsResponse(id, "PLAN", features));
    }

    public record TenantEntitlementsResponse(
            UUID targetId,
            String targetType,
            Map<String, Boolean> plan,
            Map<String, Boolean> override,
            Map<String, Boolean> effective
    ) {}

    @GetMapping("/tenants/{id}/entitlements")
    @Operation(summary = "Read tenant plan, override and effective entitlements (FEATURE_TOGGLE)")
    public ResponseEntity<TenantEntitlementsResponse> getTenantEntitlements(
            @PathVariable UUID id,
            Authentication auth
    ) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorize(principal, "FEATURE_TOGGLE");
        if (tenantRepository == null) {
            throw new IllegalStateException("Tenant directory is unavailable for effective entitlement resolution.");
        }
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));
        Map<String, Boolean> plan = new LinkedHashMap<>();
        if (planEntitlementRepository != null) {
            planEntitlementRepository.findByPlanId(tenant.getPlanId()).forEach(entitlement ->
                    plan.put(featureKey(entitlement.getModuleCode(), entitlement.getFeatureCode()), entitlement.isEnabled()));
        }
        Map<String, Boolean> override = new LinkedHashMap<>();
        if (tenantEntitlementRepository != null) {
            tenantEntitlementRepository.findByTenantId(id).forEach(entitlement ->
                    override.put(featureKey(entitlement.getModuleCode(), entitlement.getFeatureCode()), entitlement.isEnabled()));
        }
        Map<String, Boolean> effective = new LinkedHashMap<>(plan);
        effective.putAll(override);
        return ResponseEntity.ok(new TenantEntitlementsResponse(id, "TENANT", plan, override, effective));
    }

    private static String featureKey(String moduleCode, String featureCode) {
        return featureCode == null || featureCode.isBlank() ? moduleCode : moduleCode + ":" + featureCode;
    }

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
