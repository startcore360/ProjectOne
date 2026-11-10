package com.edu360.api.guardian.dto;

import jakarta.validation.constraints.NotBlank;

public record GuardianChangeRequestDto(
        @NotBlank String reason
) {
}
