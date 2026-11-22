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
        String sectionName,

        @jakarta.validation.constraints.Min(value = 1, message = "capacity must be at least 1")
        @jakarta.validation.constraints.Max(value = 10000, message = "capacity cannot exceed 10000")
        Integer capacity,

        UUID classTeacherUserId
) {
    public CreateSectionRequest(UUID academicSessionId, UUID semesterId, UUID classId, String sectionName) {
        this(academicSessionId, semesterId, classId, sectionName, null, null);
    }
}
