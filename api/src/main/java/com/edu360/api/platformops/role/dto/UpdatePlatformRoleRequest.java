package com.edu360.api.platformops.role.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePlatformRoleRequest(
        @NotBlank @Size(min = 2, max = 100) String roleName,
        @NotNull @Valid List<RoleActionItem> actions
) {
}
