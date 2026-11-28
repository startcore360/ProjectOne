package com.edu360.api.platformops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/platform/platform-users/{id}/mfa-reset (MFA-01): why the reset is needed. */
public record PlatformMfaResetRequest(
        @NotBlank(message = "Give a reason for the reset")
        @Size(min = 5, max = 500, message = "The reason must be 5 to 500 characters")
        String reason) {
}
