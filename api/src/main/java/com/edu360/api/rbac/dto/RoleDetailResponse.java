package com.edu360.api.rbac.dto;

import java.util.List;
import java.util.UUID;

import com.edu360.api.rbac.CustomScope;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionStatus;
import com.edu360.api.rbac.RoleLevel;
import com.edu360.api.rbac.RoleOrigin;

public record RoleDetailResponse(
        UUID roleDefinitionId,
        UUID tenantId,
        String roleCode,
        String roleName,
        RoleLevel roleLevel,
        RoleOrigin roleOrigin,
        CustomScope customScope,
        boolean sensitiveRole,
        RoleDefinitionStatus status,
        List<String> actionCodes
) {
    public static RoleDetailResponse from(RoleDefinition role, List<String> actions) {
        return new RoleDetailResponse(
                role.getId().getRoleDefinitionId(),
                role.getId().getTenantId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getRoleLevel(),
                role.getRoleOrigin(),
                role.getCustomScope(),
                role.isSensitiveRole(),
                role.getStatus(),
                actions != null ? actions : List.of()
        );
    }
}
