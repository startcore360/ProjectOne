package com.edu360.api.platformops.release.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.platformops.release.PlatformRelease;

public record ReleaseResponse(
        UUID releaseId,
        String versionLabel,
        String releaseNotes,
        boolean isHotfix,
        String status,
        Instant deployedAt,
        Instant rolledBackAt,
        String schemaVersion,
        Instant createdAt,
        Integer rowVersion
) {
    public static ReleaseResponse fromEntity(PlatformRelease r) {
        return new ReleaseResponse(
                r.getReleaseId(),
                r.getVersionLabel(),
                r.getReleaseNotes(),
                r.isHotfix(),
                r.getStatus(),
                r.getDeployedAt(),
                r.getRolledBackAt(),
                r.getSchemaVersion(),
                r.getCreatedAt(),
                r.getRowVersion()
        );
    }
}
