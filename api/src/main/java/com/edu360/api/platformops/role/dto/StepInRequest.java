package com.edu360.api.platformops.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StepInRequest(
        @NotBlank String actionCode,
        @NotBlank @Size(min = 5, max = 500, message = "Give a reason of at least 5 characters") String reason
) {
}
