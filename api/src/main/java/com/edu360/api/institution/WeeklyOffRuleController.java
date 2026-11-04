package com.edu360.api.institution;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.WeeklyOffRuleRequest;
import com.edu360.api.institution.dto.WeeklyOffRuleResponse;
import com.edu360.api.rbac.RbacPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * WHAT: WEEKLY_OFF_SET - see WeeklyOffRuleService's javadoc. institutionId is a query param on
 * GET and a body field on POST, same reasoning as CalendarEventController.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/weekly-off-rules")
@RequiredArgsConstructor
@Tag(name = "Institution setup", description = "Weekly-off pattern (WEEKLY_OFF_SET)")
public class WeeklyOffRuleController {

    private final WeeklyOffRuleService weeklyOffRuleService;

    @PostMapping
    @Operation(summary = "Set a weekly-off rule for an institution (WEEKLY_OFF_SET)")
    public ResponseEntity<WeeklyOffRuleResponse> setRule(
            @PathVariable UUID tenantId,
            @Valid @RequestBody WeeklyOffRuleRequest request,
            Authentication authentication) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(weeklyOffRuleService.setRule(principal, request));
    }

    @GetMapping
    @Operation(summary = "List the weekly-off rules for an institution (WEEKLY_OFF_SET)")
    public ResponseEntity<List<WeeklyOffRuleResponse>> listRules(
            @PathVariable UUID tenantId,
            @RequestParam UUID institutionId,
            Authentication authentication) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(weeklyOffRuleService.listRules(principal, institutionId));
    }

    private RbacPrincipal tenantPrincipal(UUID tenantId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("WEEKLY_OFF_SET is a tenant-side action; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the URL does not match the authenticated tenant.");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}
