package com.edu360.api.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateGradingSchemeRequest(
        UUID institutionId,
        @NotBlank @Size(min = 2, max = 100) String schemeName,
        @NotNull LocalDate effectiveFrom
) {}
