package com.edu360.api.auth;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.platformops.entitlement.ModuleEntitlementService;
import com.edu360.api.platformops.security.SecurityPolicyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * What the signed-in browser needs to apply the platform policy on its side:
 * - idleTimeoutMinutes (I-06): the saved policy's idle timeout, used by the idle tracker;
 * - disabledModules (I-07): module codes switched off for the caller's tenant, so the menu hides
 *   them. The server still refuses their endpoints (403 MODULE_NOT_ENABLED) whatever the menu shows.
 * Allowed during a forced password change (MustChangePasswordFilter), so the change screen also
 * uses the real idle timeout.
 */
@RestController
@RequestMapping({"/api/auth", "/v1/auth"})
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class SessionSettingsController {

    private final SecurityPolicyService securityPolicyService;
    private final ModuleEntitlementService moduleEntitlementService;

    public record SessionSettingsResponse(int idleTimeoutMinutes, List<String> disabledModules) {
    }

    @GetMapping("/session-settings")
    @Operation(summary = "Idle timeout and switched-off modules for the signed-in user")
    public ResponseEntity<SessionSettingsResponse> sessionSettings(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int idle = securityPolicyService.current().idleSessionTimeoutMinutes();
        Set<String> disabled = switch (authentication.getPrincipal()) {
            case TenantPrincipal tenant -> moduleEntitlementService.disabledModules(tenant.tenantId());
            case PlatformPrincipal platform -> Set.of();
            case null, default -> null;
        };
        if (disabled == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new SessionSettingsResponse(idle, List.copyOf(disabled)));
    }
}
