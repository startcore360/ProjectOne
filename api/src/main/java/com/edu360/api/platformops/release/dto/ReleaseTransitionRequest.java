package com.edu360.api.platformops.release.dto;

import jakarta.validation.constraints.NotBlank;

public record ReleaseTransitionRequest(
        @NotBlank String targetStatus
) {}
