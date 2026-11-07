package com.edu360.api.termlock.dto;

import jakarta.validation.constraints.NotBlank;

public record UnlockTermRequest(
        @NotBlank String reason,
        Integer unlockDurationHours
) {
}
