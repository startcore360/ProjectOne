package com.edu360.api.institution.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Request body for PUT /api/tenants/{tenantId}/departments/{id}/hod (HOD_ASSIGN). */
public record HodAssignRequest(@NotNull UUID staffAssignmentId) {
}
