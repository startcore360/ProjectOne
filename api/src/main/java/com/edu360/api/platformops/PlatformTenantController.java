package com.edu360.api.platformops;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.idempotency.PlatformIdempotentExecutor;
import com.edu360.api.platformops.dto.CreatePlanRequest;
import com.edu360.api.platformops.dto.PlanResponse;
import com.edu360.api.platformops.dto.TenantActivationRequest;
import com.edu360.api.platformops.dto.TenantCreateRequest;
import com.edu360.api.platformops.dto.TenantLifecycleActionRequest;
import com.edu360.api.platformops.dto.TenantPageResponse;
import com.edu360.api.platformops.dto.TenantResponse;
import com.edu360.api.platformops.dto.TenantSettingsResponse;
import com.edu360.api.platformops.dto.UpdatePlanRequest;
import com.edu360.api.platformops.dto.UpdateTenantSettingsRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller exposing tenant onboarding and plan retrieval for platform operators:
 * - TENANT_CREATE: POST /v1/platform/tenants (starts in TRIAL state)
 * - Tenant Directory: GET /v1/platform/tenants
 * - Plan Catalogue: GET /v1/platform/plans
 */
@RestController
@RequestMapping({"/v1/platform", "/api/platform"})
@RequiredArgsConstructor
@Tag(name = "Platform Tenants", description = "Tenant provisioning and subscription plans")
public class PlatformTenantController {

    private final PlatformPermissionService platformPermissionService;
    private final TenantManagementService tenantManagementService;
    /** E8-S5: Idempotency-Key handling for the D5 operations marked IdempotencyKey. */
    private final PlatformIdempotentExecutor idempotentExecutor;

    @GetMapping("/plans")
    @Operation(summary = "List subscription plans")
    public ResponseEntity<List<PlanResponse>> listPlans(Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorizeAny(principal, List.of("PLAN_MANAGE", "TENANT_CREATE", "INVOICE_MANAGE"));
        return ResponseEntity.ok(tenantManagementService.listPlans());
    }

    @PostMapping("/plans")
    @Operation(summary = "Create subscription plan (PLAN_MANAGE / Rule F-4)")
    public ResponseEntity<PlanResponse> createPlan(
            @Valid @RequestBody CreatePlanRequest request,
            Authentication authentication) {

        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "PLAN_MANAGE");

        PlanResponse created = tenantManagementService.createPlan(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/plans/{planId}")
    @Operation(summary = "Get subscription plan with an ETag")
    public ResponseEntity<PlanResponse> getPlan(@PathVariable UUID planId, Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "PLAN_MANAGE");
        PlanResponse response = tenantManagementService.getPlan(planId);
        return ResponseEntity.ok().eTag(new PlatformConcurrency().etag(response.rowVersion())).body(response);
    }

    @PutMapping("/plans/{planId}")
    @Operation(summary = "Update subscription plan (PLAN_MANAGE / Rule F-4 / If-Match)")
    public ResponseEntity<PlanResponse> updatePlan(
            @PathVariable UUID planId,
            @Valid @RequestBody UpdatePlanRequest request,
            @RequestHeader("If-Match") String ifMatch,
            Authentication authentication) {

        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "PLAN_MANAGE");

        PlanResponse updated = tenantManagementService.updatePlan(planId, request, principal, ifMatch);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/tenants/{tenantId}/suspension")
    @Operation(summary = "Suspend tenant (TENANT_SUSPEND_ARCHIVE / Rule R2-24; Idempotency-Key + If-Match)")
    public ResponseEntity<TenantResponse> suspendTenant(
            @PathVariable UUID tenantId,
            @RequestBody(required = false) TenantLifecycleActionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "TENANT_SUSPEND_ARCHIVE");
        String reason = request != null ? request.reason() : null;
        return idempotentExecutor.execute(principal.platformUserId(), "suspendTenant", idempotencyKey,
                fingerprint(tenantId, reason), HttpStatus.OK, TenantResponse.class,
                () -> tenantManagementService.suspendTenant(tenantId, reason, principal, ifMatch));
    }

    /** Internal / test path: no HTTP headers, no version check. */
    public ResponseEntity<TenantResponse> suspendTenant(
            UUID tenantId,
            TenantLifecycleActionRequest request,
            Authentication authentication) {

        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "TENANT_SUSPEND_ARCHIVE");

        String reason = request != null ? request.reason() : null;
        TenantResponse response = tenantManagementService.suspendTenant(tenantId, reason, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tenants/{tenantId}/archive")
    @Operation(summary = "Archive tenant (TENANT_SUSPEND_ARCHIVE / Rule R2-24; Idempotency-Key + If-Match)")
    public ResponseEntity<TenantResponse> archiveTenant(
            @PathVariable UUID tenantId,
            @RequestBody(required = false) TenantLifecycleActionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "TENANT_SUSPEND_ARCHIVE");
        String reason = request != null ? request.reason() : null;
        return idempotentExecutor.execute(principal.platformUserId(), "archiveTenant", idempotencyKey,
                fingerprint(tenantId, reason), HttpStatus.OK, TenantResponse.class,
                () -> tenantManagementService.archiveTenant(tenantId, reason, principal, ifMatch));
    }

    /** Internal / test path: no HTTP headers, no version check. */
    public ResponseEntity<TenantResponse> archiveTenant(
            UUID tenantId,
            TenantLifecycleActionRequest request,
            Authentication authentication) {

        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "TENANT_SUSPEND_ARCHIVE");

        String reason = request != null ? request.reason() : null;
        TenantResponse response = tenantManagementService.archiveTenant(tenantId, reason, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tenants/{tenantId}/reactivation")
    @Operation(summary = "Reactivate suspended tenant to ACTIVE state")
    public ResponseEntity<TenantResponse> reactivateTenant(
            @PathVariable UUID tenantId,
            Authentication authentication) {

        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "TENANT_SUSPEND_ARCHIVE");

        TenantResponse response = tenantManagementService.reactivateTenant(tenantId, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tenants/{tenantId}/settings")
    @Operation(summary = "Get tenant operational settings and limits (TENANT_SETTINGS)")
    public ResponseEntity<TenantSettingsResponse> getTenantSettings(
            @PathVariable UUID tenantId,
            Authentication authentication) {

        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "TENANT_SETTINGS");

        TenantSettingsResponse response = tenantManagementService.getTenantSettings(tenantId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/tenants/{tenantId}/settings")
    @Operation(summary = "Update tenant operational settings and limits (TENANT_SETTINGS / Rules PV-07, WF-06)")
    public ResponseEntity<TenantSettingsResponse> updateTenantSettings(
            @PathVariable UUID tenantId,
            @RequestBody UpdateTenantSettingsRequest request,
            Authentication authentication) {

        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "TENANT_SETTINGS");

        TenantSettingsResponse response = tenantManagementService.updateTenantSettings(tenantId, request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tenants")
    @Operation(summary = "Create a tenant in TRIAL state (TENANT_CREATE; Idempotency-Key)")
    public ResponseEntity<TenantResponse> createTenant(
            @Valid @RequestBody TenantCreateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "TENANT_CREATE");
        // The stored replay holds the one-time admin password only for this caller and key; it is never
        // returned by any other endpoint (TenantResponse comment).
        return idempotentExecutor.execute(principal.platformUserId(), "createTenant", idempotencyKey,
                request, HttpStatus.CREATED, TenantResponse.class,
                () -> tenantManagementService.createTenant(request, principal));
    }

    /** Internal / test path: no Idempotency-Key. */
    public ResponseEntity<TenantResponse> createTenant(
            TenantCreateRequest request,
            Authentication authentication) {

        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "TENANT_CREATE");

        TenantResponse created = tenantManagementService.createTenant(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/tenants/{tenantId}/provisioning")
    @Operation(summary = "The tenant's setup steps P01-P07 in run order, with status and error (E1-S2)")
    public ResponseEntity<com.edu360.api.platformops.dto.TenantProvisioningStatusResponse> getProvisioningStatus(
            @PathVariable UUID tenantId, Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorizeAny(principal, List.of("TENANT_CREATE", "TENANT_SETTINGS"));
        return ResponseEntity.ok(tenantManagementService.getProvisioningStatus(tenantId));
    }

    @PostMapping("/tenants/{tenantId}/provisioning/retry")
    @Operation(summary = "Retry a failed tenant setup from the first step that is not DONE (TENANT_CREATE, E1-S2)")
    public ResponseEntity<TenantResponse> retryProvisioning(
            @PathVariable UUID tenantId,
            @Valid @RequestBody(required = false) com.edu360.api.platformops.dto.TenantProvisioningRetryRequest request,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "TENANT_CREATE");
        TenantResponse response = tenantManagementService.retryProvisioning(tenantId,
                request == null ? null : request.retentionMonths(),
                request == null ? null : request.backupRetentionDays(), principal);
        // May carry a one-time temporary password (PD-10): never cache it.
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
    }

    @PostMapping("/tenants/{tenantId}/first-admin/password-reset")
    @Operation(summary = "Re-issue a temporary password for the tenant's first admin (TENANT_SETTINGS)")
    public ResponseEntity<com.edu360.api.platformops.dto.TenantFirstAdminPasswordResetResponse> resetFirstAdminPassword(
            @PathVariable UUID tenantId,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "TENANT_SETTINGS");
        com.edu360.api.platformops.dto.TenantFirstAdminPasswordResetResponse response =
                tenantManagementService.resetFirstAdminPassword(tenantId, principal);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
    }

    @GetMapping("/tenants")
    @Operation(summary = "List all tenants with pagination")
    public ResponseEntity<TenantPageResponse> listTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {

        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorizeAny(principal, List.of("TENANT_CREATE", "TENANT_SETTINGS",
                "TENANT_ACTIVATE", "TENANT_SUSPEND_ARCHIVE", "INVOICE_MANAGE", "SUPPORT_TICKET_MANAGE"));

        int safeSize = Math.min(Math.max(size, 1), 200);
        TenantPageResponse response = tenantManagementService.listTenants(
                PageRequest.of(Math.max(page, 0), safeSize)
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tenants/{tenantId}/activation")
    @Operation(summary = "Activate a tenant after payment verification (TENANT_ACTIVATE; Idempotency-Key + If-Match)")
    public ResponseEntity<TenantResponse> activateTenant(
            @PathVariable UUID tenantId,
            @RequestBody(required = false) TenantActivationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "TENANT_ACTIVATE");
        return idempotentExecutor.execute(principal.platformUserId(), "activateTenant", idempotencyKey,
                fingerprint(tenantId, request == null ? null : String.valueOf(request)), HttpStatus.OK,
                TenantResponse.class,
                () -> tenantManagementService.activateTenant(tenantId, request, principal, ifMatch));
    }

    /** Internal / test path: no HTTP headers, no version check. */
    public ResponseEntity<TenantResponse> activateTenant(
            UUID tenantId,
            TenantActivationRequest request,
            Authentication authentication) {

        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "TENANT_ACTIVATE");

        TenantResponse response = tenantManagementService.activateTenant(tenantId, request, principal);
        return ResponseEntity.ok(response);
    }


    /** Request fingerprint for path-plus-optional-text operations (null-safe, unlike Map.of). */
    private static java.util.Map<String, Object> fingerprint(UUID tenantId, Object detail) {
        java.util.Map<String, Object> fp = new java.util.LinkedHashMap<>();
        fp.put("tenantId", tenantId);
        fp.put("detail", detail);
        return fp;
    }

    private PlatformPrincipal requirePlatformPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("Platform credentials required; no platform principal on this request.");
        }
        return principal;
    }
}
