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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Program Rules", description = "Program and curriculum rule sets [PROGRAM_RULES_SET / Action #30]")
public class ProgramRuleController {

    private final ProgramRuleService programRuleService;

    @GetMapping({"/v1/tenants/{tenantId}/program-rule-sets", "/api/tenants/{tenantId}/program-rule-sets"})
    @Operation(summary = "List program rule sets for a tenant")
    public ResponseEntity<List<ProgramRuleSetResponse>> listRuleSets(
            @PathVariable UUID tenantId,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(programRuleService.listRuleSets(tenantId, principal));
    }

    @GetMapping({"/v1/tenants/{tenantId}/program-rule-sets/{id}", "/api/tenants/{tenantId}/program-rule-sets/{id}"})
    @Operation(summary = "Get a program rule set by ID")
    public ResponseEntity<ProgramRuleSetResponse> getRuleSet(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(programRuleService.getRuleSet(tenantId, principal, id));
    }

    @PostMapping({"/v1/tenants/{tenantId}/program-rule-sets", "/api/tenants/{tenantId}/program-rule-sets"})
    @Operation(summary = "Create program and curriculum rules [PROGRAM_RULES_SET / Action #30]")
    public ResponseEntity<ProgramRuleSetResponse> createRuleSet(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateProgramRuleSetRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(programRuleService.createRuleSet(tenantId, principal, request));
    }

    @PostMapping({"/v1/tenants/{tenantId}/program-rule-sets/{id}/activation", "/api/tenants/{tenantId}/program-rule-sets/{id}/activation"})
    @Operation(summary = "Activate a draft program rule set")
    public ResponseEntity<ProgramRuleSetResponse> activateRuleSet(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(programRuleService.activateRuleSet(tenantId, principal, id));
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
