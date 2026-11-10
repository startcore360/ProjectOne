package com.edu360.api.selfservice.dto;

import java.util.UUID;
import com.edu360.api.rbac.ScopeType;

/**
 * Result of POST /v1/tenants/{tenantId}/me/active-role (and /api/me/active-role).
 */
public record SwitchActiveRoleResponse(
        UUID assignmentId,
        String roleCode,
        String roleName,
        ScopeType scopeType,
        UUID institutionId,
        UUID departmentId,
        String message
) {}
