package com.edu360.api.platformops;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;

/**
 * WHAT: the platform-scoped sibling of {@code rbac.PermissionEvaluatorService.can(...)} for the
 * four read-mostly platform stories (PLATFORM_AUDIT_VIEW, USAGE_COUNTS_VIEW, BACKUP_STATUS_VIEW,
 * INTEGRATION_TYPES_MANAGE). It is deliberately NOT a call into
 * {@code PermissionEvaluatorService}, even though the task brief that created this package asked
 * for "the same pattern as UserCreationController" - see WHY below.
 *
 * WHY this is a separate, hand-rolled check instead of reusing PermissionEvaluatorService:
 * {@code PermissionEvaluatorService.can(principal, actionCode, scope, target)} takes an
 * {@code rbac.RbacPrincipal(UUID tenantId, UUID userAccountId)} and matches it against
 * app.user_role_assignment rows, which are keyed by tenant_id. A request authenticated against
 * the platform realm produces a {@link PlatformPrincipal}, which has NO tenantId field at all -
 * see that class's own javadoc on why a platform login must never carry tenant context (the
 * "Platform Admin must never become an implicit RLS bypass" rule). Fabricating or borrowing a
 * tenantId to force a PlatformPrincipal through RbacPrincipal would be exactly that anti-pattern.
 * There is also no app.user_role_assignment row for a platform user in the first place: platform
 * staff hold one of the six fixed {@link PlatformRole} values directly, never a tenant
 * role_definition. So the tenant-shaped evaluator is structurally the wrong tool here, not just
 * an inconvenient one.
 *
 * WHAT this checks against instead: sql/05_ref_data.sql already seeds the ground truth for these
 * four actions in ref.action_catalog / ref.standard_role_action - PRODUCT_OWNER and
 * PLATFORM_ADMIN are the only platform roles bound to PLATFORM_AUDIT_VIEW (ids 1684/1685),
 * USAGE_COUNTS_VIEW (1686/1687) and INTEGRATION_TYPES_MANAGE (1682/1683); BACKUP_STATUS_VIEW
 * (1689-1692) additionally lists the tenant-side IT_ADMINISTRATOR/SCHOOL_IT_SUPPORT roles for a
 * tenant's OWN backup status, which is a different, tenant-scoped story this package does not
 * build (see BackupStatusController's javadoc). ALLOWED_ROLES below mirrors exactly the
 * PRODUCT_OWNER/PLATFORM_ADMIN rows for all four actions, per this package's own brief ("Who:
 * Product Owner, Platform Admin").
 *
 * An unrecognized action code is always denied, never silently allowed - same fail-closed
 * default as PermissionEvaluatorService.can(...).
 */
@Service
public class PlatformPermissionService {

    private static final Map<String, Set<PlatformRole>> ALLOWED_ROLES = Map.of(
            "PLATFORM_AUDIT_VIEW", Set.of(PlatformRole.PRODUCT_OWNER, PlatformRole.PLATFORM_ADMIN),
            "USAGE_COUNTS_VIEW", Set.of(PlatformRole.PRODUCT_OWNER, PlatformRole.PLATFORM_ADMIN),
            "BACKUP_STATUS_VIEW", Set.of(PlatformRole.PRODUCT_OWNER, PlatformRole.PLATFORM_ADMIN),
            "INTEGRATION_TYPES_MANAGE", Set.of(PlatformRole.PRODUCT_OWNER, PlatformRole.PLATFORM_ADMIN));

    public void authorize(PlatformPrincipal principal, String actionCode) {
        Set<PlatformRole> allowedRoles = ALLOWED_ROLES.get(actionCode);
        if (allowedRoles == null) {
            throw new UnauthorizedException("Action " + actionCode + " is not a recognized platform action.");
        }
        if (principal == null) {
            throw new UnauthorizedException(actionCode + " requires an authenticated platform principal.");
        }
        if (!allowedRoles.contains(principal.platformRole())) {
            throw new UnauthorizedException(
                    "Platform role " + principal.platformRole() + " does not hold action " + actionCode + ".");
        }
    }
}
