package com.edu360.api.platformops.release;

import java.time.Instant;
import java.util.UUID;

public record ReleaseHealthResponse(
        UUID releaseId,
        String versionLabel,
        Instant deployedAt,
        Window before,
        Window after
) {
    public record Window(
            Instant from,
            Instant to,
            long failedAuditEvents,
            Long http5xxRequests,
            boolean complete
    ) {
    }
}
