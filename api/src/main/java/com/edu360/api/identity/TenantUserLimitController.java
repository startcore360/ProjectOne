package com.edu360.api.identity;

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
 * E1-S5 (WF-01): how much of the tenant's user limit is in use, for the Users page banner
 * (warning at 90%, blocked at 100%). Signed-in users of that tenant only.
 */
@RestController
@RequestMapping({"/v1/tenants/{tenantId}", "/api/tenants/{tenantId}"})
@Tag(name = "Users", description = "User limit of the tenant")
public class TenantUserLimitController {

    private final UserAccountService userAccountService;

    public TenantUserLimitController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/user-limit")
    @Operation(summary = "Active accounts against the tenant's user limit: {limit, active, percent}")
    public ResponseEntity<UserAccountService.UserLimit> userLimit(@PathVariable UUID tenantId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)
                || !tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("The user limit is available only to signed-in users of this tenant.");
        }
        return ResponseEntity.ok(userAccountService.currentUserLimit(tenantId));
    }
}
