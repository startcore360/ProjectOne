package com.edu360.api.rbac.dto;

import java.util.List;

import com.edu360.api.rbac.CustomScope;
import com.edu360.api.rbac.RoleLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCustomRoleRequest(
        @NotBlank(message = "Role code is required")
        @Pattern(regexp = "^[A-Z0-9_]{3,50}$", message = "Role code must be 3-50 uppercase alphanumeric characters or underscores")
        String roleCode,

        @NotBlank(message = "Role name is required")
        @Size(min = 2, max = 100, message = "Role name must be between 2 and 100 characters")
        String roleName,

        @NotNull(message = "Role level is required")
        RoleLevel roleLevel,

        @NotNull(message = "Custom scope is required")
        CustomScope customScope,

        List<String> actionCodes
) {}
