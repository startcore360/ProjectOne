package com.edu360.api.institution.dto;

import java.util.UUID;

/** Response for a successful CLASS_TEACHER_ASSIGN. */
public record ClassTeacherAssignResponse(UUID sectionId, UUID classTeacherAssignmentId, UUID staffAssignmentId) {
}
