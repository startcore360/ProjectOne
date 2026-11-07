package com.edu360.api.academic;

import java.time.Instant;
import java.util.UUID;

public record SubjectAllocationResponse(
        UUID subjectAllocationId,
        UUID institutionId,
        UUID courseOfferingId,
        UUID sectionId,
        UUID subjectGroupId,
        UUID staffAssignmentId,
        String status,
        Instant createdAt
) {
    public static SubjectAllocationResponse from(SubjectAllocation a) {
        return new SubjectAllocationResponse(
                a.getId().getSubjectAllocationId(),
                a.getInstitutionId(),
                a.getCourseOfferingId(),
                a.getSectionId(),
                a.getSubjectGroupId(),
                a.getStaffAssignmentId(),
                a.getStatus(),
                a.getCreatedAt()
        );
    }
}
