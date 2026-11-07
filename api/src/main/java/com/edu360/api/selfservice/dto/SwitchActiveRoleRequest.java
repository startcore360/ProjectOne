package com.edu360.api.selfservice.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for POST /v1/tenants/{tenantId}/me/active-role (and /api/me/active-role).
 */
public record SwitchActiveRoleRequest(
        @NotNull(message = "assignmentId is required")
        UUID assignmentId
) {}
