package com.edu360.api.platformops.support.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GrantSupportAccessRequest(
        @NotNull UUID ticketId,
        UUID institutionId,
        @NotNull @Min(1) @Max(24) Integer durationHours
) {}
