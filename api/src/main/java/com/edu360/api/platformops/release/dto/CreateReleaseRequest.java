package com.edu360.api.platformops.release.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReleaseRequest(
        @NotBlank @Size(max = 40) String versionLabel,
        @NotBlank @Size(max = 20000) String releaseNotes,
        boolean isHotfix,
        @NotBlank @Size(max = 60) String schemaVersion
) {}
