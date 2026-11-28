package com.edu360.api.t10;

import java.util.UUID;

public record DispatchMessage(
        UUID dispatchId,
        UUID tenantId,
        UUID institutionId,
        UUID campaignId,
        UUID recipientUserId,
        UUID createdBy,
        String channel,
        String phone,
        String email,
        String body) {}
