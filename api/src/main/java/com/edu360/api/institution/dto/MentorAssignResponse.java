package com.edu360.api.institution.dto;

import java.util.UUID;

/** Response for a successful MENTOR_ASSIGN. */
public record MentorAssignResponse(UUID sectionId, UUID mentorAssignmentId, UUID staffAssignmentId) {
}
