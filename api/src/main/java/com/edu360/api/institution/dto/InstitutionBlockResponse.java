package com.edu360.api.institution.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of institution block request or decision.
 */
public record InstitutionBlockResponse(
        UUID institutionId,
        String institutionCode,
        String institutionName,
        String status,
        Instant blockedAt,
        String message
) {}
