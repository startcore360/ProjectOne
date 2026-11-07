package com.edu360.api.platformops.support.dto;

import java.time.Instant;
import java.util.UUID;

public record AssumeSupportSessionResponse(
        UUID supportSessionId,
        UUID ticketId,
        UUID tenantId,
        String ticketNumber,
        String sessionToken,
        Instant expiresAt,
        String accessMode
) {}
