package com.edu360.api.platformops.dto;

import java.util.UUID;

public record AuditActorResponse(
        UUID actorUserId,
        String displayName,
        String email,
        String actorType
) {}
