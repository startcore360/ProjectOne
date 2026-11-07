package com.edu360.api.academic;

import java.time.Instant;
import java.util.UUID;

public record StudentStatusResponse(
        UUID studentProfileId,
        UUID institutionId,
        String studentCode,
        String status,
        String statusReason,
        Instant statusChangedAt
) {}
