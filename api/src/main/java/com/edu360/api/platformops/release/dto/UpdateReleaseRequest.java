package com.edu360.api.platformops.release.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateReleaseRequest(
        @NotBlank(message = "Release notes are required")
        @Size(max = 20000, message = "Release notes cannot exceed 20000 characters")
        String releaseNotes,

        boolean isHotfix,

        @NotBlank(message = "Schema version is required")
        @Size(max = 60, message = "Schema version cannot exceed 60 characters")
        String schemaVersion
) {}
