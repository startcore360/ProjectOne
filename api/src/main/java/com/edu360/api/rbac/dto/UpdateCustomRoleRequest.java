package com.edu360.api.rbac.dto;

import com.edu360.api.rbac.RoleDefinitionStatus;
import jakarta.validation.constraints.Size;

public record UpdateCustomRoleRequest(
        @Size(min = 2, max = 100, message = "Role name must be between 2 and 100 characters")
        String roleName,
        String description,
        RoleDefinitionStatus status
) {}
