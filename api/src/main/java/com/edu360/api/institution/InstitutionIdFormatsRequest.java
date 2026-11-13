package com.edu360.api.institution;

import jakarta.validation.constraints.NotBlank;

public record InstitutionIdFormatsRequest(
        @NotBlank String studentIdFormat,
        String staffIdFormat
) {}
