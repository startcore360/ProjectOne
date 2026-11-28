package com.edu360.api.institution.dto;

import java.util.UUID;

public record MentorAssignResponse(UUID sectionId, UUID mentorAssignmentId,
        UUID staffAssignmentId, int rowVersion) {
    public MentorAssignResponse(UUID sectionId, UUID mentorAssignmentId, UUID staffAssignmentId) {
        this(sectionId, mentorAssignmentId, staffAssignmentId, 1);
    }
}
