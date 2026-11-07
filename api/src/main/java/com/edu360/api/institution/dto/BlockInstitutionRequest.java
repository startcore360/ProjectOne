package com.edu360.api.institution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for POST /v1/tenants/{tenantId}/institutions/{id}/block-requests (Rule RL-01).
 * Reason for block is required.
 */
public record BlockInstitutionRequest(
        @NotBlank(message = "Reason for blocking is required [Rule RL-01]")
        @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters")
        String reason
) {}
