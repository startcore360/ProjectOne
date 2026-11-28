package com.edu360.api.selfservice;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserAccountService;
import com.edu360.api.institution.SectionRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Tenant-wide counts for the signed-in user's own role dashboard (RoleWorkspace.tsx) - the same
 * two aggregates already computed elsewhere (TenantUserLimitController's active-user count,
 * SectionRepository's section rows), read together in one call. No RBAC action gate, same as
 * TenantUserLimitController: these are non-sensitive aggregate counts of the caller's own tenant,
 * not a record list.
 *
 * Tenant-wide, not institution-scoped: correct for a single-institution (School/College) tenant,
 * where the whole tenant is the one institution; would over-count for a UNIVERSITY_GROUP tenant
 * with several institutions. True per-institution scoping needs real institution_ids in the RLS
 * GUCs, which nothing in production code passes yet (see TenantUserLookup's own comment) - left
 * for that broader fix rather than solved here.
 */
@RestController
@RequestMapping({"/v1/tenants/{tenantId}/dashboard", "/api/tenants/{tenantId}/dashboard"})
@Tag(name = "Dashboard", description = "Tenant-wide counts for the signed-in user's own dashboard")
public class DashboardSummaryController {

    public record DashboardSummary(long activeUsers, long activeSections) {
    }

    private final UserAccountService userAccountService;
    private final SectionRepository sectionRepository;

    public DashboardSummaryController(UserAccountService userAccountService, SectionRepository sectionRepository) {
        this.userAccountService = userAccountService;
        this.sectionRepository = sectionRepository;
    }

    @GetMapping("/summary")
    @Operation(summary = "Active user and active section counts for the tenant: {activeUsers, activeSections}")
    public ResponseEntity<DashboardSummary> summary(@PathVariable UUID tenantId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)
                || !tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("The dashboard summary is available only to signed-in users of this tenant.");
        }
        long activeUsers = userAccountService.currentUserLimit(tenantId).active();
        long activeSections = sectionRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
        return ResponseEntity.ok(new DashboardSummary(activeUsers, activeSections));
    }
}
