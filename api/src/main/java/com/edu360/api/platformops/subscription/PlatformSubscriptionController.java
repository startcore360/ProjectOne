package com.edu360.api.platformops.subscription;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.subscription.dto.AddonDecisionRequest;
import com.edu360.api.platformops.subscription.dto.CommercialCatalogItemRequest;
import com.edu360.api.platformops.subscription.dto.CommercialCatalogItemResponse;
import com.edu360.api.platformops.subscription.dto.CustomerQuoteRequest;
import com.edu360.api.platformops.subscription.dto.CustomerQuoteResponse;
import com.edu360.api.platformops.subscription.dto.PlanComponentRequest;
import com.edu360.api.platformops.subscription.dto.PlanComponentResponse;
import com.edu360.api.platformops.subscription.dto.TenantAddonResponse;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform/subscriptions", "/api/platform/subscriptions"})
@RequiredArgsConstructor
public class PlatformSubscriptionController {
    private final PlatformSubscriptionService subscriptionService;
    private final PlatformPermissionService permissionService;

    @GetMapping("/catalog")
    @Operation(summary = "List configurable plan and add-on items")
    public ResponseEntity<List<CommercialCatalogItemResponse>> listCatalog(
            @RequestParam(defaultValue = "false") boolean includeUnavailable, Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorizeAny(principal, List.of("PLAN_MANAGE", "TENANT_CREATE", "INVOICE_MANAGE"));
        return ResponseEntity.ok(subscriptionService.listCatalog(includeUnavailable));
    }

    @PostMapping("/catalog")
    public ResponseEntity<CommercialCatalogItemResponse> createCatalogItem(
            @Valid @RequestBody CommercialCatalogItemRequest request, Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, "PLAN_MANAGE");
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.createCatalogItem(request, principal));
    }

    @PutMapping("/catalog/{itemId}")
    public ResponseEntity<CommercialCatalogItemResponse> updateCatalogItem(
            @PathVariable UUID itemId,
            @Valid @RequestBody CommercialCatalogItemRequest request,
            @RequestHeader("If-Match") String ifMatch,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, "PLAN_MANAGE");
        return ResponseEntity.ok(subscriptionService.updateCatalogItem(itemId, request, principal, ifMatch));
    }

    @GetMapping("/plans/{planId}/components")
    public ResponseEntity<List<PlanComponentResponse>> listPlanComponents(
            @PathVariable UUID planId, Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorizeAny(principal, List.of("PLAN_MANAGE", "TENANT_CREATE", "INVOICE_MANAGE"));
        return ResponseEntity.ok(subscriptionService.listPlanComponents(planId));
    }

    @PutMapping("/plans/{planId}/components")
    public ResponseEntity<List<PlanComponentResponse>> replacePlanComponents(
            @PathVariable UUID planId,
            @Valid @RequestBody List<@Valid PlanComponentRequest> requests,
            @RequestHeader("If-Match") String ifMatch,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, "PLAN_MANAGE");
        return ResponseEntity.ok(subscriptionService.replacePlanComponents(planId, requests, principal, ifMatch));
    }

    @PostMapping("/quote")
    @Operation(summary = "Compare active plans against customer capacity and module requirements")
    public ResponseEntity<CustomerQuoteResponse> quote(
            @Valid @RequestBody CustomerQuoteRequest request, Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorizeAny(principal, List.of("PLAN_MANAGE", "TENANT_CREATE"));
        return ResponseEntity.ok(subscriptionService.quote(request));
    }

    @GetMapping("/addon-requests")
    public ResponseEntity<List<TenantAddonResponse>> listAddonRequests(
            @RequestParam(required = false) String status, Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorizeAny(principal, List.of("PLAN_MANAGE", "INVOICE_MANAGE"));
        return ResponseEntity.ok(subscriptionService.listAddonRequests(status));
    }

    @PostMapping("/addon-requests/{addonId}/approve")
    public ResponseEntity<TenantAddonResponse> approveAddon(
            @PathVariable UUID addonId,
            @Valid @RequestBody(required = false) AddonDecisionRequest request,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, "PLAN_MANAGE");
        return ResponseEntity.ok(subscriptionService.approveAddon(addonId, request, principal));
    }

    @PostMapping("/addon-requests/{addonId}/reject")
    public ResponseEntity<TenantAddonResponse> rejectAddon(
            @PathVariable UUID addonId,
            @Valid @RequestBody(required = false) AddonDecisionRequest request,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, "PLAN_MANAGE");
        return ResponseEntity.ok(subscriptionService.rejectAddon(addonId, request, principal));
    }

    @PostMapping("/addon-requests/{addonId}/invoice")
    public ResponseEntity<TenantAddonResponse> invoiceAddon(
            @PathVariable UUID addonId, Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, "INVOICE_MANAGE");
        return ResponseEntity.ok(subscriptionService.invoiceAddon(addonId, principal));
    }

    private static PlatformPrincipal requirePlatformPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("A platform principal is required.");
        }
        return principal;
    }
}
