package com.edu360.api.platformops;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * E1-S4: the app shell of a signed-in tenant user reads its own tenant's branding, to show the
 * logo and apply the colours. Any signed-in user of that tenant may read it; nobody else.
 */
@RestController
@RequestMapping({"/v1/tenants/{tenantId}/branding", "/api/tenants/{tenantId}/branding"})
@Tag(name = "Tenant", description = "The tenant's own branding for the app shell")
public class TenantBrandingReadController {

    private final TenantBrandingService brandingService;

    public TenantBrandingReadController(TenantBrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @GetMapping
    @Operation(summary = "This tenant's logo state and colours")
    public ResponseEntity<TenantBrandingService.Branding> branding(@PathVariable UUID tenantId,
                                                                   Authentication authentication) {
        requireOwnTenant(authentication, tenantId);
        return ResponseEntity.ok(brandingService.get(tenantId));
    }

    @GetMapping("/logo")
    @Operation(summary = "This tenant's logo image")
    public ResponseEntity<byte[]> logo(@PathVariable UUID tenantId, Authentication authentication) {
        requireOwnTenant(authentication, tenantId);
        return PlatformTenantBrandingController.LogoResponses.of(brandingService.logo(tenantId));
    }

    private static void requireOwnTenant(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)
                || !tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("Branding is available only to signed-in users of this tenant.");
        }
    }
}
