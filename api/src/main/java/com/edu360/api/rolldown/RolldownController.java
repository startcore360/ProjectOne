package com.edu360.api.rolldown;

import com.edu360.api.config.TenantPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Role Rolldown", description = "Role roll-down map and statutory override endpoints [ROLLDOWN_VIEW, ROLLDOWN_OVERRIDE_SET / Actions #23 & #24]")
public class RolldownController {

    private final RolldownService rolldownService;

    @GetMapping({
            "/v1/tenants/{tenantId}/rolldown-map",
            "/v1/tenants/{tenantId}/rolldown/map",
            "/api/tenants/{tenantId}/rolldown-map",
            "/api/tenants/{tenantId}/rolldown/map"
    })
    @Operation(summary = "View who acts for each missing role (roll-down map) [ROLLDOWN_VIEW / Action #23]")
    public ResponseEntity<List<RolldownMapEntryDto>> getRolldownMap(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) String context,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(rolldownService.getRolldownMap(tenantId, institutionId, context, principal));
    }

    @GetMapping({
            "/v1/tenants/{tenantId}/rolldown-overrides",
            "/v1/tenants/{tenantId}/rolldown/overrides",
            "/api/tenants/{tenantId}/rolldown-overrides",
            "/api/tenants/{tenantId}/rolldown/overrides"
    })
    @Operation(summary = "List rolldown overrides for a tenant or institution")
    public ResponseEntity<List<RolldownOverrideDto>> getOverrides(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) UUID institutionId,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(rolldownService.getOverrides(tenantId, institutionId, principal));
    }

    @PostMapping({
            "/v1/tenants/{tenantId}/rolldown-overrides",
            "/v1/tenants/{tenantId}/rolldown/overrides",
            "/api/tenants/{tenantId}/rolldown-overrides",
            "/api/tenants/{tenantId}/rolldown/overrides"
    })
    @Operation(summary = "Set a rolldown override or withhold a non-approval right [ROLLDOWN_OVERRIDE_SET / Action #24]")
    public ResponseEntity<RolldownOverrideDto> createOverride(
            @PathVariable UUID tenantId,
            @RequestHeader(name = "X-Active-Role", required = false) String activeRole,
            @Valid @RequestBody CreateRolldownOverrideRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        RolldownOverrideDto response = rolldownService.createOverride(tenantId, principal, activeRole, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping({
            "/v1/tenants/{tenantId}/rolldown-overrides/{id}/revoke",
            "/v1/tenants/{tenantId}/rolldown-overrides/{id}/revocation",
            "/v1/tenants/{tenantId}/rolldown/overrides/{id}/revocation",
            "/api/tenants/{tenantId}/rolldown-overrides/{id}/revoke",
            "/api/tenants/{tenantId}/rolldown-overrides/{id}/revocation",
            "/api/tenants/{tenantId}/rolldown/overrides/{id}/revocation"
    })
    @Operation(summary = "Revoke an active rolldown override")
    public ResponseEntity<RolldownOverrideDto> revokeOverride(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(rolldownService.revokeOverride(tenantId, principal, id));
    }

    @DeleteMapping({
            "/v1/tenants/{tenantId}/rolldown-overrides/{id}",
            "/api/tenants/{tenantId}/rolldown-overrides/{id}"
    })
    @Operation(summary = "Delete / revoke an active rolldown override via standard DELETE")
    public ResponseEntity<RolldownOverrideDto> deleteOverride(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(rolldownService.revokeOverride(tenantId, principal, id));
    }

    private TenantPrincipal extractPrincipal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal p)) {
            throw new AccessDeniedException("Authentication required");
        }
        if (!tenantId.equals(p.tenantId())) {
            throw new AccessDeniedException("Cross-tenant access forbidden");
        }
        return p;
    }
}
