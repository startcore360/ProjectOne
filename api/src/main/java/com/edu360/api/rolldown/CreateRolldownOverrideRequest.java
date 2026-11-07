package com.edu360.api.rolldown;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateRolldownOverrideRequest(
        UUID institutionId,
        @NotBlank String missingRoleCode,
        @NotBlank String permissionKind,
        String actionCode,
        @NotBlank String mode,
        String targetRoleCode,
        @NotBlank @Size(min = 5, max = 500) String reason
) {}
