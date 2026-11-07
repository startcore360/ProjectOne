package com.edu360.api.platformops.role.dto;

import java.util.List;

import com.edu360.api.platform.PlatformRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SavePlatformRoleTemplateRequest(
        @NotBlank @Size(min = 2, max = 100) String templateName,
        @Size(max = 500) String description,
        @NotNull PlatformRole basePlatformRole,
        @NotNull List<@NotBlank String> actionCodes
) {
}
