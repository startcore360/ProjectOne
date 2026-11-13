package com.edu360.api.identity;

import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudentCreateRequest(
        @NotNull UUID institutionId,
        @NotBlank @Size(max = 150) String fullName,
        LocalDate dateOfBirth,
        LocalDate admissionDate,
        @NotNull UUID sectionId,
        @NotNull UUID academicSessionId,
        @Schema(description = "Optional override. College defaults to enabled; school follows its class login switch.")
        Boolean createLogin,
        @Size(max = 30) String rollNumber
) {
    /** Backwards-compatible constructor for existing API tests and clients. */
    public StudentCreateRequest(UUID institutionId, String fullName, LocalDate dateOfBirth,
            LocalDate admissionDate, UUID sectionId, UUID academicSessionId, Boolean createLogin) {
        this(institutionId, fullName, dateOfBirth, admissionDate, sectionId, academicSessionId,
                createLogin, null);
    }
}
