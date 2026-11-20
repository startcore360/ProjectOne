package com.edu360.api.rbac.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST .../users/{userId}/mfa-reset (MFA-01): the acting scope and why the reset is needed. */
public record MfaResetRequest(
        @NotNull UUID institutionId,
        @NotBlank(message = "Give a reason for the reset")
        @Size(min = 5, max = 500, message = "The reason must be 5 to 500 characters")
        String reason) {
}
