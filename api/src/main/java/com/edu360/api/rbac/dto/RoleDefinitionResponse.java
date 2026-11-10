package com.edu360.api.rbac.dto;

import java.util.UUID;

import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionStatus;
import com.edu360.api.rbac.RoleLevel;
import com.edu360.api.rbac.RoleOrigin;

public record RoleDefinitionResponse(
        UUID roleDefinitionId,
        UUID tenantId,
        String roleCode,
        String roleName,
        RoleLevel roleLevel,
        RoleOrigin roleOrigin,
        boolean sensitiveRole,
        RoleDefinitionStatus status
) {
    public static RoleDefinitionResponse from(RoleDefinition role) {
        return new RoleDefinitionResponse(
                role.getId().getRoleDefinitionId(),
                role.getId().getTenantId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getRoleLevel(),
                role.getRoleOrigin(),
                role.isSensitiveRole(),
                role.getStatus()
        );
    }
}
