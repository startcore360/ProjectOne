package com.edu360.api.guardian;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.guardian.dto.GuardianChangeDecisionRequest;
import com.edu360.api.guardian.dto.GuardianChangeRequestDto;
import com.edu360.api.guardian.dto.GuardianLinkResponse;
import com.edu360.api.guardian.dto.LinkGuardianRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Guardian Links", description = "Endpoints for student guardian linkages and change approval [GUARDIAN_LINK, GUARDIAN_CHANGE_REQUEST, GUARDIAN_CHANGE_APPROVE]")
public class GuardianLinkController {

    private final GuardianLinkService guardianLinkService;

    @GetMapping({
            "/v1/tenants/{tenantId}/students/{studentId}/guardian-links",
            "/api/tenants/{tenantId}/students/{studentId}/guardian-links"
    })
    @Operation(summary = "List guardian links for a student")
    public ResponseEntity<List<GuardianLinkResponse>> listGuardianLinks(
            @PathVariable UUID tenantId,
            @PathVariable UUID studentId,
            Authentication authentication
    ) {
        extractPrincipal(authentication, tenantId);
        List<GuardianLinkResponse> responses = guardianLinkService.listGuardianLinks(tenantId, studentId);
        return ResponseEntity.ok(responses);
    }

    @PostMapping({
            "/v1/tenants/{tenantId}/students/{studentId}/guardian-links",
            "/api/tenants/{tenantId}/students/{studentId}/guardian-links"
    })
    @Operation(summary = "Link a guardian to a student [GUARDIAN_LINK / Action #10]")
    public ResponseEntity<GuardianLinkResponse> linkGuardian(
            @PathVariable UUID tenantId,
            @PathVariable UUID studentId,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody LinkGuardianRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        UUID effectiveInstId = institutionId != null ? institutionId : UUID.randomUUID();
        GuardianLinkResponse response = guardianLinkService.linkGuardian(
                tenantId, effectiveInstId, studentId, principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping({
            "/v1/tenants/{tenantId}/guardian-links/{id}/change-requests",
            "/api/tenants/{tenantId}/guardian-links/{id}/change-requests"
    })
    @Operation(summary = "Request guardian change or block [GUARDIAN_CHANGE_REQUEST / Action #11]")
    public ResponseEntity<GuardianLinkResponse> requestChange(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody GuardianChangeRequestDto request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        UUID effectiveInstId = institutionId != null ? institutionId : UUID.randomUUID();
        GuardianLinkResponse response = guardianLinkService.requestChange(
                tenantId, effectiveInstId, id, principal, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping({
            "/v1/tenants/{tenantId}/guardian-links/{id}/change-approvals",
            "/api/tenants/{tenantId}/guardian-links/{id}/change-approvals"
    })
    @Operation(summary = "Approve or reject guardian change [GUARDIAN_CHANGE_APPROVE / Action #12]")
    public ResponseEntity<GuardianLinkResponse> decideChange(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody GuardianChangeDecisionRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        UUID effectiveInstId = institutionId != null ? institutionId : UUID.randomUUID();
        GuardianLinkResponse response = guardianLinkService.decideChange(
                tenantId, effectiveInstId, id, principal, request);
        return ResponseEntity.ok(response);
    }

    private TenantPrincipal extractPrincipal(Authentication authentication, UUID pathTenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Authenticated tenant principal required.");
        }
        if (!principal.tenantId().equals(pathTenantId)) {
            throw new UnauthorizedException("Tenant mismatch between path and authentication.");
        }
        return principal;
    }
}
