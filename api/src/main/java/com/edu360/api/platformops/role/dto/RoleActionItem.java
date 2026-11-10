package com.edu360.api.platformops.role.dto;

import com.edu360.api.platformops.role.AccessMode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoleActionItem(@NotBlank String actionCode, @NotNull AccessMode accessMode) {
}
