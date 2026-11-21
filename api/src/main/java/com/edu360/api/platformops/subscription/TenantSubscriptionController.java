package com.edu360.api.platformops.subscription;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.subscription.dto.TenantAddonRequest;
import com.edu360.api.platformops.subscription.dto.TenantAddonResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tenants/{tenantId}/subscription/addon-requests")
@RequiredArgsConstructor
public class TenantSubscriptionController {
    private final TenantSubscriptionRequestService requestService;

    @GetMapping
    public ResponseEntity<List<TenantAddonResponse>> list(
            @PathVariable UUID tenantId, Authentication authentication) {
        return ResponseEntity.ok(requestService.list(tenantId, requireTenantPrincipal(authentication)));
    }

    @PostMapping
    public ResponseEntity<TenantAddonResponse> request(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantAddonRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requestService.request(tenantId, requireTenantPrincipal(authentication), request));
    }

    private static TenantPrincipal requireTenantPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("A tenant principal is required.");
        }
        return principal;
    }
}
