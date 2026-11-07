package com.edu360.api.institution.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a degree program under STRUCTURE_MANAGE.
 * Validates programCode per ck_program_program_code: ^[A-Z0-9_.-]{2,20}$
 * Validates programName per ck_program_program_name: length 2 to 200.
 * Validates durationYears per ck_program_duration_years: 1 to 10.
 * Validates semestersPerYear per ck_program_semesters_per_year: 1 to 6.
 */
public record CreateProgramRequest(
        @NotNull(message = "Department ID is required")
        UUID departmentId,

        @NotBlank(message = "Program code is required")
        @Pattern(regexp = "^[A-Z0-9_.-]{2,20}$", message = "Program code must be 2-20 characters matching ^[A-Z0-9_.-]{2,20}$")
        String programCode,

        @NotBlank(message = "Program name is required")
        @Size(min = 2, max = 200, message = "Program name must be between 2 and 200 characters")
        String programName,

        @NotNull(message = "Duration years is required")
        @Min(value = 1, message = "Duration years must be at least 1")
        @Max(value = 10, message = "Duration years cannot exceed 10")
        Short durationYears,

        @Min(value = 1, message = "Semesters per year must be at least 1")
        @Max(value = 6, message = "Semesters per year cannot exceed 6")
        Short semestersPerYear
) {
    public Short effectiveSemestersPerYear() {
        return semestersPerYear != null ? semestersPerYear : (short) 2;
    }
}
