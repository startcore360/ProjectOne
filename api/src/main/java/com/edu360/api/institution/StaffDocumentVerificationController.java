package com.edu360.api.institution;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;

import jakarta.validation.Valid;

@RestController
@RequestMapping({"/v1/tenants/{tenantId}/staff-documents/{id}/verification",
        "/api/tenants/{tenantId}/staff-documents/{id}/verification"})
public class StaffDocumentVerificationController {
    private final StaffDocumentService service;

    public StaffDocumentVerificationController(StaffDocumentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<StaffDocumentResponse> verify(@PathVariable UUID tenantId,
            @PathVariable("id") UUID staffDocumentId,
            @Valid @RequestBody StaffDocumentVerificationRequest request,
            Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Tenant authentication is required.");
        }
        if (!tenantId.equals(principal.tenantId())) throw new ResourceNotFoundException("Staff document not found.");
        return ResponseEntity.ok(service.verify(principal, staffDocumentId,
                request.verificationStatus(), request.remark()));
    }
}
