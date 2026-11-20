package com.edu360.api.institution;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.RbacPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping({"/v1/tenants/{tenantId}/institutions/{institutionId}/id-formats",
        "/api/tenants/{tenantId}/institutions/{institutionId}/id-formats"})
public class InstitutionIdFormatController {
    private final InstitutionIdFormatService service;

    public InstitutionIdFormatController(InstitutionIdFormatService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<InstitutionIdFormatsResponse> get(@PathVariable UUID tenantId,
            @PathVariable UUID institutionId, Authentication authentication) {
        return ResponseEntity.ok(service.get(principal(authentication, tenantId), institutionId));
    }

    @PutMapping
    public ResponseEntity<InstitutionIdFormatsResponse> set(@PathVariable UUID tenantId,
            @PathVariable UUID institutionId, @Valid @RequestBody InstitutionIdFormatsRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(service.set(principal(authentication, tenantId), institutionId, request));
    }

    private static RbacPrincipal principal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Tenant authentication is required.");
        }
        if (!tenantId.equals(principal.tenantId())) throw new ResourceNotFoundException("Institution not found.");
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}
