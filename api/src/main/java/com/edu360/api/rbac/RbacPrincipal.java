package com.edu360.api.rbac;

import java.util.UUID;

/**
 * WHAT: the "who" half of PermissionEvaluatorService.can(principal, actionCode, scope,
 * target) - deliberately just the two IDs the evaluator needs (never a password, token or
 * display name), so it can be built from either a TenantPrincipal
 * (config.TenantPrincipal.tenantId()/userAccountId()) in a real request, or by hand in a test,
 * without this package depending on config's Authentication-principal types at all.
 */
public record RbacPrincipal(UUID tenantId, UUID userAccountId) {
}
