package com.edu360.api.imports.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record UserImportRequest(
        @NotBlank(message = "CSV content is required")
        String csvContent,
        UUID institutionId,
        String importKind
) {}
