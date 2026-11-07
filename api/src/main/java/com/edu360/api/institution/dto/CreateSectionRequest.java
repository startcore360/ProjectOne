package com.edu360.api.institution.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSectionRequest(
        @NotNull(message = "academicSessionId is required")
        UUID academicSessionId,

        UUID semesterId,

        UUID classId,

        @NotBlank(message = "sectionName is required")
        @Size(min = 1, max = 20, message = "sectionName must be between 1 and 20 characters")
        String sectionName
) {}
