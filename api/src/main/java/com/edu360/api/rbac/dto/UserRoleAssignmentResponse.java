package com.edu360.api.rbac.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;

public record UserRoleAssignmentResponse(
        UUID userRoleAssignmentId,
        UUID tenantId,
        UUID userAccountId,
        UUID roleDefinitionId,
        String roleCode,
        String roleName,
        ScopeType scopeType,
        UUID institutionId,
        UUID departmentId,
        AssignmentStatus status,
        Instant effectiveFrom,
        Instant effectiveTo
) {
    public static UserRoleAssignmentResponse from(UserRoleAssignment assignment, RoleDefinition roleDefinition) {
        return new UserRoleAssignmentResponse(
                assignment.getId().getUserRoleAssignmentId(),
                assignment.getId().getTenantId(),
                assignment.getUserAccountId(),
                assignment.getRoleDefinitionId(),
                roleDefinition != null ? roleDefinition.getRoleCode() : null,
                roleDefinition != null ? roleDefinition.getRoleName() : null,
                assignment.getScopeType(),
                assignment.getInstitutionId(),
                assignment.getDepartmentId(),
                assignment.getStatus(),
                assignment.getEffectiveFrom(),
                assignment.getEffectiveTo()
        );
    }
}
