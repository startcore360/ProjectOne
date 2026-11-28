package com.edu360.api.institution;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.CreateCurriculumVersionRequest;
import com.edu360.api.institution.dto.CurriculumVersionResponse;
import com.edu360.api.rbac.RbacPrincipal;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping({
        "/v1/tenants/{tenantId}/institutions/{institutionId}/programs/{programId}/curriculum-versions",
        "/api/tenants/{tenantId}/institutions/{institutionId}/programs/{programId}/curriculum-versions"
})
public class ProgramCurriculumVersionController {
    private final ProgramCurriculumVersionService service;

    public ProgramCurriculumVersionController(ProgramCurriculumVersionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<CurriculumVersionResponse>> list(
            @PathVariable UUID tenantId, @PathVariable UUID institutionId, @PathVariable UUID programId,
            Authentication authentication) {
        return ResponseEntity.ok(service.list(requirePrincipal(tenantId, authentication), institutionId, programId));
    }

    @GetMapping("/{versionId}")
    public ResponseEntity<CurriculumVersionResponse> get(
            @PathVariable UUID tenantId, @PathVariable UUID institutionId, @PathVariable UUID programId,
            @PathVariable UUID versionId, Authentication authentication) {
        return ResponseEntity.ok(service.get(requirePrincipal(tenantId, authentication), institutionId, programId, versionId));
    }

    @PostMapping
    public ResponseEntity<CurriculumVersionResponse> create(
            @PathVariable UUID tenantId, @PathVariable UUID institutionId, @PathVariable UUID programId,
            @Valid @RequestBody CreateCurriculumVersionRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(requirePrincipal(tenantId, authentication), institutionId, programId, request));
    }

    private RbacPrincipal requirePrincipal(UUID tenantId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Curriculum version management requires a tenant principal.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the URL does not match the authenticated tenant.");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}
