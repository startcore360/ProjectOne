package com.edu360.api.platformops.dto;

import java.time.Instant;

/** A persisted provisioning step, presented in the agreed execution order. */
public record TenantProvisioningStepResponse(
        int executionOrder,
        String planStep,
        String stepCode,
        String description,
        String status,
        int attempts,
        String lastErrorCode,
        Instant startedAt,
        Instant finishedAt
) {
}
