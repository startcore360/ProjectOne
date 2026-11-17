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

    /** E2-S2: module/feature lists and dependencies; optional so hand-built test fixtures still work. */
    private EntitlementCatalog catalog;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCatalog(EntitlementCatalog catalog) {
        this.catalog = catalog;
    }

    public record EditorFeature(String featureCode, String key, String description, boolean provisional,
                                boolean enabled, boolean planEnabled, String source) {}

    public record EditorModule(String moduleCode, String moduleName, int phase, boolean enabled, boolean planEnabled,
                               String source, java.util.List<String> requires, java.util.List<EditorFeature> features) {}

    public record EntitlementEditorResponse(UUID targetId, String targetType, java.util.List<EditorModule> modules) {}

    @GetMapping("/plans/{id}/entitlement-editor")
    @Operation(summary = "Plan modules grouped with their features, phase and provisional flag (E2-S2)")
    public ResponseEntity<EntitlementEditorResponse> getPlanEditor(@PathVariable UUID id, Authentication auth) {
        permissionService.authorize(requirePlatformPrincipal(auth), "FEATURE_TOGGLE");
        Map<String, Boolean> plan = planMap(id);
        return ResponseEntity.ok(new EntitlementEditorResponse(id, "PLAN", editor(plan, plan, Map.of(), false)));
    }

    @GetMapping("/tenants/{id}/entitlement-editor")
    @Operation(summary = "Tenant modules: effective value, plan value and whether it is an override (E2-S2)")
    public ResponseEntity<EntitlementEditorResponse> getTenantEditor(@PathVariable UUID id, Authentication auth) {
        permissionService.authorize(requirePlatformPrincipal(auth), "FEATURE_TOGGLE");
        Map<String, Boolean> plan = planMap(tenant(id).getPlanId());
        Map<String, Boolean> override = overrideMap(id);
        Map<String, Boolean> effective = new LinkedHashMap<>(plan);
        effective.putAll(override);
        return ResponseEntity.ok(new EntitlementEditorResponse(id, "TENANT", editor(effective, plan, override, true)));
    }

    private java.util.List<EditorModule> editor(Map<String, Boolean> effective, Map<String, Boolean> plan,
                                                Map<String, Boolean> override, boolean tenantView) {
        if (catalog == null) {
            return java.util.List.of();
        }
        return catalog.modules().stream().map(m -> new EditorModule(m.moduleCode(), m.moduleName(), m.phase(),
                on(effective, m.moduleCode()), on(plan, m.moduleCode()),
                tenantView ? (override.containsKey(m.moduleCode()) ? "OVERRIDE" : "PLAN") : null,
                m.requires(),
                m.features().stream().map(f -> {
                    String key = featureKey(m.moduleCode(), f.featureCode());
                    return new EditorFeature(f.featureCode(), key, f.description(), f.provisional(), on(effective, key),
                            on(plan, key), tenantView ? (override.containsKey(key) ? "OVERRIDE" : "PLAN") : null);
                }).toList())).toList();
    }

    private static boolean on(Map<String, Boolean> state, String key) {
        return Boolean.TRUE.equals(state.get(key));
    }

    private Map<String, Boolean> planMap(UUID planId) {
        Map<String, Boolean> plan = new LinkedHashMap<>();
        if (planEntitlementRepository != null && planId != null) {
            planEntitlementRepository.findByPlanId(planId).forEach(e ->
                    plan.put(featureKey(e.getModuleCode(), e.getFeatureCode()), e.isEnabled()));
        }
        return plan;
    }

    private Map<String, Boolean> overrideMap(UUID tenantId) {
        Map<String, Boolean> override = new LinkedHashMap<>();
        if (tenantEntitlementRepository != null) {
            tenantEntitlementRepository.findByTenantId(tenantId).forEach(e ->
                    override.put(featureKey(e.getModuleCode(), e.getFeatureCode()), e.isEnabled()));
        }
        return override;
    }

    private Tenant tenant(UUID id) {
        if (tenantRepository == null) {
            throw new IllegalStateException("Tenant directory is unavailable for effective entitlement resolution.");
        }
        return tenantRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));
    }

    private Map<String, java.util.Set<String>> requires() {
        return catalog == null ? Map.of() : EntitlementCatalog.requiresMap(catalog.modules());
    }

    private static Map<String, Map<String, Object>> changesOf(Map<String, Boolean> before, Map<String, Boolean> requested) {
        Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
        requested.forEach((key, value) -> {
            boolean now = Boolean.TRUE.equals(value);
            if (on(before, key) != now) {
                Map<String, Object> c = new java.util.HashMap<>();
                c.put("old", on(before, key));
                c.put("new", now);
                changes.put(key, c);
            }
        });
        return changes;
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
            Map<String, Boolean> features,
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
        return ResponseEntity.ok(new TenantEntitlementsResponse(id, "TENANT", effective, plan, override, effective));
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

        // E2-S2: a feature needs its module on; a module needs what it requires and can't be
        // turned off while another module that needs it is on.
        Map<String, Boolean> before = planMap(id);
        Map<String, Boolean> requested = req.features() == null ? Map.of() : req.features();
        Map<String, Boolean> result = new LinkedHashMap<>(before);
        result.putAll(requested);
        EntitlementRules.validate(result, requested, requires());

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

        auditWriter.record(principal, null, "CONFIG_CHANGE", "UPDATE", "FEATURE_TOGGLE", "PLAN", id,
                PlatformAuditWriter.SUCCESS, changesOf(before, requested));

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

        Map<String, Boolean> requested = req.features() == null ? Map.of() : req.features();
        Map<String, Boolean> plan = tenantRepository == null ? Map.of() : planMap(tenant(id).getPlanId());
        Map<String, Boolean> override = overrideMap(id);
        Map<String, Boolean> before = new LinkedHashMap<>(plan);
        before.putAll(override);
        // E2-S2 / WF-06: switching on what the plan leaves off is the Product Owner's decision only.
        java.util.List<String> beyond = EntitlementRules.beyondPlan(plan, requested);
        if (!beyond.isEmpty() && principal.platformRole() != com.edu360.api.platform.PlatformRole.PRODUCT_OWNER) {
            auditWriter.record(principal, id, "CONFIG_CHANGE", "UPDATE", "FEATURE_TOGGLE", "TENANT", id, "DENIED",
                    Map.of("beyondPlan", Map.of("new", String.join(",", beyond))));
            throw new com.edu360.api.exception.ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "BEYOND_PLAN_PO_ONLY", "Beyond the plan - only the Product Owner can turn on "
                            + String.join(", ", beyond) + " for this tenant.");
        }
        Map<String, Boolean> result = new LinkedHashMap<>(before);
        result.putAll(requested);
        EntitlementRules.validate(result, requested, requires());

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

        auditWriter.record(principal, id, "CONFIG_CHANGE", "UPDATE", "FEATURE_TOGGLE", "TENANT", id,
                PlatformAuditWriter.SUCCESS, changesOf(before, requested));

        return ResponseEntity.ok(new EntitlementsResponse(id, "TENANT", req.features()));
    }

    private PlatformPrincipal requirePlatformPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("Platform credentials required; no platform principal on this request.");
        }
        return principal;
    }
}
