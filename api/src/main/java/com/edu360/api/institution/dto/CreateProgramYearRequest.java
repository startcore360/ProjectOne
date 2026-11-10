package com.edu360.api.institution.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProgramYearRequest(
        @NotNull(message = "yearNumber is required")
        @Min(value = 1, message = "yearNumber must be between 1 and 10")
        @Max(value = 10, message = "yearNumber must be between 1 and 10")
        Short yearNumber,

        @NotBlank(message = "label is required")
        @Size(min = 1, max = 30, message = "label must be between 1 and 30 characters")
        String label
) {}
