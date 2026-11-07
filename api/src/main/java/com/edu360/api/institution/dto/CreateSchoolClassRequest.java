package com.edu360.api.institution.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSchoolClassRequest(
        @NotBlank(message = "className is required")
        @Size(min = 1, max = 30, message = "className must be between 1 and 30 characters")
        String className,

        @NotNull(message = "displayOrder is required")
        @Min(value = 0, message = "displayOrder must be non-negative")
        Short displayOrder
) {}
