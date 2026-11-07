package com.edu360.api.institution.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSubjectRequest(
        @NotBlank(message = "subjectCode is required")
        @Pattern(regexp = "^[A-Z0-9_.-]{2,20}$", message = "subjectCode must be 2-20 characters of uppercase letters, numbers, dot, dash, or underscore")
        String subjectCode,

        @NotBlank(message = "subjectName is required")
        @Size(min = 2, max = 200, message = "subjectName must be between 2 and 200 characters")
        String subjectName,

        @DecimalMin(value = "0.0", message = "credits must be non-negative")
        BigDecimal credits,

        Boolean isElective
) {
    public boolean effectiveIsElective() {
        return Boolean.TRUE.equals(isElective);
    }
}
