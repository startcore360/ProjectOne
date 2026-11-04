package com.edu360.api.institution.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Request body for PUT /api/tenants/{tenantId}/sections/{id}/class-teacher (CLASS_TEACHER_ASSIGN). */
public record ClassTeacherAssignRequest(@NotNull UUID staffAssignmentId) {
}
