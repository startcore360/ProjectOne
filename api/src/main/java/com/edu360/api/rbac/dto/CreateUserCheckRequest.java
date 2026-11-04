package com.edu360.api.rbac.dto;

import java.util.UUID;

import com.edu360.api.rbac.ScopeType;

import jakarta.validation.constraints.NotNull;

/**
 * WHAT: the request body for the USER_CREATE proof-of-integration endpoint
 * (POST /api/tenants/{tenantId}/users) - just enough to describe WHERE the new user would be
 * created (its scope), so PermissionEvaluatorService has something real to check against the
 * caller's own role assignment. There is deliberately no displayName/email/userType field here
 * yet: this endpoint proves the evaluator is wired end-to-end, it does not implement the full
 * USER_CREATE story (validation, Keycloak provisioning, audit, etc. are a separate, later
 * piece of work - see the controller's own javadoc).
 */
public record CreateUserCheckRequest(
        @NotNull ScopeType scopeType,
        UUID institutionId,
        UUID departmentId) {
}
