package com.edu360.api.academic;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Grading Scheme", description = "Grading scheme version management [GRADING_SCHEME_SET / Action #34]")
public class GradingSchemeController {

    private final GradingSchemeService gradingSchemeService;

    @GetMapping({"/v1/tenants/{tenantId}/grading-schemes", "/api/tenants/{tenantId}/grading-schemes"})
    @Operation(summary = "List grading schemes for a tenant or institution")
    public ResponseEntity<List<GradingSchemeResponse>> listGradingSchemes(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) UUID institutionId,
            Authentication authentication
    ) {
        extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(gradingSchemeService.listGradingSchemes(tenantId, institutionId));
    }

    @PostMapping({"/v1/tenants/{tenantId}/grading-schemes", "/api/tenants/{tenantId}/grading-schemes"})
    @Operation(summary = "Create grading scheme [GRADING_SCHEME_SET / Action #34]")
    public ResponseEntity<GradingSchemeResponse> createGradingScheme(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateGradingSchemeRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(gradingSchemeService.createGradingScheme(tenantId, principal, request));
    }

    @PostMapping({"/v1/tenants/{tenantId}/grading-schemes/{id}/activation", "/api/tenants/{tenantId}/grading-schemes/{id}/activation"})
    @Operation(summary = "Activate a draft grading scheme")
    public ResponseEntity<GradingSchemeResponse> activateGradingScheme(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(gradingSchemeService.activateGradingScheme(tenantId, principal, id));
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
