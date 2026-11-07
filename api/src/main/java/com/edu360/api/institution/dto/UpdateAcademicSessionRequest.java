package com.edu360.api.institution.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAcademicSessionRequest(
        @NotBlank(message = "sessionLabel is required")
        @Size(min = 1, max = 30, message = "sessionLabel must be 1 to 30 characters")
        String sessionLabel,

        @NotNull(message = "startDate is required")
        LocalDate startDate,

        @NotNull(message = "endDate is required")
        LocalDate endDate,

        @Pattern(regexp = "^(PLANNED|ACTIVE|CLOSED)$", message = "status must be PLANNED, ACTIVE, or CLOSED")
        String status,

        Boolean notifyInstitutions,

        Boolean flagAffectedTimetablesAndExams
) {}
