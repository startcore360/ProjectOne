package com.edu360.api.institution;

import java.net.URI;
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
import com.edu360.api.institution.dto.BlockDecisionRequest;
import com.edu360.api.institution.dto.BlockInstitutionRequest;
import com.edu360.api.institution.dto.CreateInstitutionRequest;
import com.edu360.api.institution.dto.InstitutionBlockResponse;
import com.edu360.api.institution.dto.InstitutionPageResponse;
import com.edu360.api.institution.dto.InstitutionResponse;
import com.edu360.api.rbac.RbacPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller exposing endpoints for:
 * - INSTITUTION_CREATE_DRAFT (Sprint 1 Story #25 / WF-16, WF-14)
 * - INSTITUTION_ACTIVATE (Sprint 1 Story #26 / WF-16, Rule C7)
 *
 * Mapped to both /v1/tenants/{tenantId}/institutions (OpenAPI spec target)
 * and /api/tenants/{tenantId}/institutions (current monolithic app convention).
 */
@RestController
@RequestMapping({"/v1/tenants/{tenantId}/institutions", "/api/tenants/{tenantId}/institutions"})
@RequiredArgsConstructor
@Tag(name = "Institution setup", description = "Institution lifecycle and registration (INSTITUTION_CREATE_DRAFT, INSTITUTION_ACTIVATE)")
public class InstitutionController {

    private final InstitutionService institutionService;

    @PostMapping
    @Operation(summary = "Create a college or school as Draft (INSTITUTION_CREATE_DRAFT)")
    public ResponseEntity<InstitutionResponse> createDraft(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateInstitutionRequest request,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        InstitutionResponse response = institutionService.createDraft(principal, request);
        return ResponseEntity.created(URI.create("/api/tenants/" + tenantId + "/institutions/" + response.institutionId()))
                .body(response);
    }

    @PostMapping("/{institutionId}/activation")
    @Operation(summary = "Approve and activate a Draft institution (INSTITUTION_ACTIVATE)")
    public ResponseEntity<InstitutionResponse> activateInstitution(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(institutionService.activateInstitution(principal, institutionId));
    }

    @PostMapping("/{institutionId}/activation-requests")
    @Operation(summary = "Request and approve institution activation (INSTITUTION_ACTIVATE)")
    public ResponseEntity<InstitutionResponse> requestActivation(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(institutionService.activateInstitution(principal, institutionId));
    }

    @GetMapping
    @Operation(summary = "List institutions for this tenant")
    public ResponseEntity<InstitutionPageResponse> listInstitutions(
            @PathVariable UUID tenantId,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(institutionService.listInstitutions(principal));
    }

    @GetMapping("/{institutionId}")
    @Operation(summary = "Get single institution by ID")
    public ResponseEntity<InstitutionResponse> getInstitution(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(institutionService.getInstitution(principal, institutionId));
    }

    @PostMapping("/{institutionId}/block-requests")
    @Operation(summary = "Ask to block an institution's access (INSTITUTION_BLOCK_REQUEST / Rule RL-01)")
    public ResponseEntity<InstitutionBlockResponse> requestBlock(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @Valid @RequestBody BlockInstitutionRequest request,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(institutionService.requestBlock(principal, institutionId, request));
    }

    @PostMapping({"/{institutionId}/block-decisions", "/{institutionId}/block"})
    @Operation(summary = "Approve or reject an institution block (INSTITUTION_BLOCK_APPROVE / Rule WF-15)")
    public ResponseEntity<InstitutionBlockResponse> decideBlock(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @Valid @RequestBody BlockDecisionRequest request,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(institutionService.decideBlock(principal, institutionId, request));
    }

    private RbacPrincipal extractTenantPrincipal(UUID tenantId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Institution operations require authenticated tenant principal.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the path does not match the authenticated tenant.");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}
