package com.edu360.api.termlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnlockTermRequest(
        @NotBlank @Size(min = 20, max = 500) String reason,
        Integer unlockDurationHours
) {
}
