package com.edu360.api.platformops.role.dto;

import java.util.List;
import java.util.UUID;

import com.edu360.api.platform.PlatformRole;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** updateTemplate: also save these actions to the source template, for later roles only (OD-62). */
public record CreatePlatformRoleRequest(
        @NotBlank @Size(min = 2, max = 100) String roleName,
        @NotNull PlatformRole basePlatformRole,
        UUID sourceTemplateId,
        @NotNull @Valid List<RoleActionItem> actions,
        boolean updateTemplate
) {
}
