package com.edu360.api.institution.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSemesterRequest(
        @NotNull(message = "semesterNumber is required")
        @Min(value = 1, message = "semesterNumber must be between 1 and 60")
        @Max(value = 60, message = "semesterNumber must be between 1 and 60")
        Short semesterNumber,

        @NotBlank(message = "label is required")
        @Size(min = 1, max = 30, message = "label must be between 1 and 30 characters")
        String label
) {}
