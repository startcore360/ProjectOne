package com.edu360.api.platformops.role.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.platformops.role.PlatformStepIn;

public record StepInResponse(
        UUID platformStepInId,
        UUID actorUserId,
        String actionCode,
        String reason,
        Instant startedAt,
        Instant expiresAt
) {
    public static StepInResponse from(PlatformStepIn stepIn) {
        return new StepInResponse(stepIn.getPlatformStepInId(), stepIn.getActorUserId(), stepIn.getActionCode(),
                stepIn.getReason(), stepIn.getStartedAt(), stepIn.getExpiresAt());
    }
}
