package com.edu360.api.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateProgramRuleSetRequest(
        UUID institutionId,
        UUID programId,
        @NotBlank String rules,
        @NotNull LocalDate effectiveFrom
) {}
