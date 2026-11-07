package com.edu360.api.selfservice.dto;

import java.util.Set;
import java.util.UUID;

import com.edu360.api.rbac.ScopeType;

public record UserPermissionsResponse(
        UUID tenantId,
        UUID userAccountId,
        String activeRoleCode,
        String activeRoleName,
        ScopeType scopeType,
        UUID institutionId,
        UUID departmentId,
        Set<String> allowedActions
) {
}
