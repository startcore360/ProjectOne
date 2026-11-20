package com.edu360.api.institution;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StaffAssignmentRequest(
        UUID institutionId,
        UUID departmentId,
        @NotBlank @Size(min = 1, max = 100) String designation,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
