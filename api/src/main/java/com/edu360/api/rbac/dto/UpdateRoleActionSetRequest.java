package com.edu360.api.rbac.dto;

import java.util.List;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleActionSetRequest(
        @NotNull(message = "Action codes list is required")
        List<String> actionCodes
) {}
