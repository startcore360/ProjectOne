package com.edu360.api.platformops;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** E1-S4: platform staff read and change a tenant's branding (TENANT_SETTINGS). */
@RestController
@RequestMapping({"/v1/platform/tenants", "/api/platform/tenants"})
@Tag(name = "Platform Tenants", description = "Tenant branding: logo on every plan, colours on plans that allow them")
public class PlatformTenantBrandingController {

    private final PlatformPermissionService platformPermissionService;
    private final TenantBrandingService brandingService;

    public PlatformTenantBrandingController(PlatformPermissionService platformPermissionService,
                                            TenantBrandingService brandingService) {
        this.platformPermissionService = platformPermissionService;
        this.brandingService = brandingService;
    }

    @GetMapping("/{tenantId}/branding")
    @Operation(summary = "A tenant's branding: whether it has a logo, its colours, and whether its plan allows colours")
    public ResponseEntity<TenantBrandingService.Branding> getBranding(@PathVariable UUID tenantId,
                                                                      Authentication authentication) {
        platformPermissionService.authorizeAny(principal(authentication), List.of("TENANT_SETTINGS", "TENANT_CREATE"));
        return ResponseEntity.ok(brandingService.get(tenantId));
    }

    @GetMapping("/{tenantId}/branding/logo")
    @Operation(summary = "The tenant's logo image")
    public ResponseEntity<byte[]> getLogo(@PathVariable UUID tenantId, Authentication authentication) {
        platformPermissionService.authorizeAny(principal(authentication), List.of("TENANT_SETTINGS", "TENANT_CREATE"));
        return LogoResponses.of(brandingService.logo(tenantId));
    }

    @PutMapping(value = "/{tenantId}/branding", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Change a tenant's logo (PNG/SVG up to 512 KB) and colours (TENANT_SETTINGS)")
    public ResponseEntity<TenantBrandingService.Branding> updateBranding(
            @PathVariable UUID tenantId,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "primary", required = false) String primary,
            @RequestParam(value = "accent", required = false) String accent,
            @RequestParam(value = "removeLogo", defaultValue = "false") boolean removeLogo,
            Authentication authentication) throws IOException {
        PlatformPrincipal principal = principal(authentication);
        platformPermissionService.authorize(principal, "TENANT_SETTINGS");
        byte[] bytes = logo == null || logo.isEmpty() ? null : logo.getBytes();
        return ResponseEntity.ok(brandingService.update(tenantId, bytes, primary, accent, removeLogo, principal));
    }

    private static PlatformPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("A platform sign-in is required.");
        }
        return principal;
    }

    /** Shared by the platform and tenant logo endpoints. */
    static final class LogoResponses {
        private LogoResponses() {
        }

        static ResponseEntity<byte[]> of(TenantBrandingService.Logo logo) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(logo.contentType()))
                    .header("X-Content-Type-Options", "nosniff")
                    // An SVG opened on its own must not run anything, even if a check were missed.
                    .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                    .body(logo.content());
        }
    }
}
