package com.edu360.api.rbac.dto;

import java.util.UUID;

import com.edu360.api.rbac.ScopeType;

import jakarta.validation.constraints.NotNull;

/**
 * WHAT: the request body for PATCH /api/tenants/{tenantId}/users/{userId} (USER_EDIT) - the
 * scope the caller is acting in (so PermissionEvaluatorService has something to check against
 * the caller's own role assignment, same shape as CreateUserCheckRequest), plus the fields that
 * may be changed. Every field below is optional on purpose: this is a partial update, so only
 * the non-null fields are applied - a field a caller omits is left exactly as it was, never
 * cleared.
 */
public record UpdateUserRequest(
        @NotNull ScopeType scopeType,
        UUID institutionId,
        UUID departmentId,
        String displayName,
        String email,
        String mobile) {
}
