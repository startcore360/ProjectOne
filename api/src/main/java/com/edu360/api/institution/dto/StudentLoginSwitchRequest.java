package com.edu360.api.institution.dto;

import jakarta.validation.constraints.NotNull;

/** Request body for PUT /api/tenants/{tenantId}/classes/{id}/student-login (STUDENT_LOGIN_SWITCH). */
public record StudentLoginSwitchRequest(@NotNull Boolean enabled) {
}
