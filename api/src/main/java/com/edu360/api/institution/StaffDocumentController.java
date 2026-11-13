package com.edu360.api.institution;

import java.util.UUID;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;

import jakarta.validation.Valid;

@RestController
@RequestMapping({"/v1/tenants/{tenantId}/staff/{id}/documents",
        "/api/tenants/{tenantId}/staff/{id}/documents"})
public class StaffDocumentController {
    private final StaffDocumentService service;

    public StaffDocumentController(StaffDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<StaffDocumentResponse>> list(@PathVariable UUID tenantId,
            @PathVariable("id") UUID staffProfileId,
            Authentication authentication) {
        TenantPrincipal principal = principal(authentication, tenantId);
        return ResponseEntity.ok(service.list(principal, staffProfileId));
    }

    @PostMapping
    public ResponseEntity<StaffDocumentResponse> attach(@PathVariable UUID tenantId,
            @PathVariable("id") UUID staffProfileId,
            @Valid @RequestBody StaffDocumentRequest request,
            Authentication authentication) {
        TenantPrincipal principal = principal(authentication, tenantId);
        return ResponseEntity.status(201).body(service.attach(principal, staffProfileId,
                request.documentType(), request.fileId()));
    }

    private static TenantPrincipal principal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Tenant authentication is required.");
        }
        if (!tenantId.equals(principal.tenantId())) throw new ResourceNotFoundException("Staff record not found.");
        return principal;
    }
}
