package com.edu360.api.institution.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateMasterCalendarEventRequest(
        @NotNull(message = "academicSessionId is required") UUID academicSessionId,
        @NotBlank(message = "eventType is required")
        @Pattern(regexp = "SEMESTER_DATES|UNIVERSITY_HOLIDAY|EXAM_DAY",
                message = "eventType must be SEMESTER_DATES, UNIVERSITY_HOLIDAY or EXAM_DAY for master calendar events")
        String eventType,
        @NotBlank(message = "title is required")
        @Size(min = 1, max = 200, message = "title must be between 1 and 200 characters")
        String title,
        @NotNull(message = "startDate is required") LocalDate startDate,
        @NotNull(message = "endDate is required") LocalDate endDate) {
}
