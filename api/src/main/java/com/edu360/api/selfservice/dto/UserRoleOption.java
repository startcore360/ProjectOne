package com.edu360.api.selfservice.dto;

import java.util.UUID;
import com.edu360.api.rbac.ScopeType;

/**
 * Representation of one active role assignment available to the authenticated user.
 */
public record UserRoleOption(
        UUID userRoleAssignmentId,
        UUID roleDefinitionId,
        String roleCode,
        String roleName,
        ScopeType scopeType,
        UUID institutionId,
        String institutionName,
        UUID departmentId,
        String departmentName,
        boolean currentActive
) {}
