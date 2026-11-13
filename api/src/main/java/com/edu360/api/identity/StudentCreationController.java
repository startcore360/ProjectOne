package com.edu360.api.identity;

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
@RequestMapping({"/v1/tenants/{tenantId}/students", "/api/tenants/{tenantId}/students"})
public class StudentCreationController {
    private final StudentCreationService service;

    public StudentCreationController(StudentCreationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<StudentProfileResponse> create(@PathVariable UUID tenantId,
            @Valid @RequestBody StudentCreateRequest request, Authentication authentication) {
        TenantPrincipal actor = principal(authentication, tenantId);
        StudentProfileResponse response = service.create(actor, request);
        return ResponseEntity.status(201).eTag(etag(response.version())).body(response);
    }

    private static TenantPrincipal principal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal actor)) {
            throw new UnauthorizedException("Tenant authentication is required.");
        }
        if (!tenantId.equals(actor.tenantId())) {
            throw new ResourceNotFoundException("Student not found.");
        }
        return actor;
    }

    private static String etag(Integer version) {
        return "\"" + (version == null ? 1 : version) + "\"";
    }
}
