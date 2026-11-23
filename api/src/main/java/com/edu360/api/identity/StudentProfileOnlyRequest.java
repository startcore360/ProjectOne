package com.edu360.api.identity;

import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Profile-only creation for admitted applicants; no section or academic-session dependency. */
public record StudentProfileOnlyRequest(
        @NotNull UUID institutionId,
        @NotBlank @Size(max=150) String fullName,
        LocalDate dateOfBirth,
        LocalDate admissionDate
) {}
