package com.edu360.api.institution.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Request body for PUT /api/tenants/{tenantId}/sections/{id}/mentor (MENTOR_ASSIGN). */
public record MentorAssignRequest(@NotNull UUID staffAssignmentId) {
}
