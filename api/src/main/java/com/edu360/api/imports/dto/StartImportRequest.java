package com.edu360.api.imports.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StartImportRequest(
        @NotBlank(message = "importMode is required")
        @Pattern(regexp = "CLEAN_ONLY|GOOD_ROWS_ONLY", message = "importMode must be either CLEAN_ONLY or GOOD_ROWS_ONLY")
        String importMode
) {}
