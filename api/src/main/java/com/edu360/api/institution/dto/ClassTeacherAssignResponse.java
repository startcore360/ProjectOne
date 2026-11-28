package com.edu360.api.institution.dto;

import java.util.UUID;

public record ClassTeacherAssignResponse(UUID sectionId, UUID classTeacherAssignmentId,
        UUID staffAssignmentId, int rowVersion) {
    public ClassTeacherAssignResponse(UUID sectionId, UUID classTeacherAssignmentId, UUID staffAssignmentId) {
        this(sectionId, classTeacherAssignmentId, staffAssignmentId, 1);
    }
}
