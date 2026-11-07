package com.edu360.api.institution.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAcademicSessionRequest(
        UUID institutionId,
        @NotBlank @Size(min = 4, max = 20, message = "sessionLabel must be between 4 and 20 characters") String sessionLabel,
        @NotNull(message = "startDate is required") LocalDate startDate,
        @NotNull(message = "endDate is required") LocalDate endDate,
        @NotNull(message = "academicYear is required") @Min(2000) @Max(2200) Integer academicYear,
        String status) {
}
