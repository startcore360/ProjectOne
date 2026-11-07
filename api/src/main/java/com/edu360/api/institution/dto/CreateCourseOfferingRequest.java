package com.edu360.api.institution.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CreateCourseOfferingRequest(
        @NotNull(message = "subjectId is required")
        UUID subjectId,

        @NotNull(message = "academicSessionId is required")
        UUID academicSessionId,

        UUID semesterId,

        UUID classId
) {}
