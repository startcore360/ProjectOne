package com.edu360.api.institution.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for POST /v1/tenants/{tenantId}/institutions/{id}/block-decisions (Rule WF-15).
 * Vice Chancellor executive approval or rejection of institution block.
 */
public record BlockDecisionRequest(
        @NotNull(message = "approved decision is required")
        Boolean approved,

        @Size(max = 500, message = "Decision notes cannot exceed 500 characters")
        String decisionNotes
) {}
