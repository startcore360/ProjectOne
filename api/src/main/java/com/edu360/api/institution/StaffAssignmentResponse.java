package com.edu360.api.institution;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StaffAssignmentResponse(
        UUID id, UUID staffProfileId, UUID institutionId, UUID departmentId,
        String designation, LocalDate effectiveFrom, LocalDate effectiveTo,
        String status, Instant createdAt, UUID createdBy, Instant updatedAt,
        UUID updatedBy, Integer version
) {
    public static StaffAssignmentResponse from(StaffAssignment a) {
        return new StaffAssignmentResponse(a.getStaffAssignmentId(), a.getStaffProfileId(),
                a.getInstitutionId(), a.getDepartmentId(), a.getDesignation(),
                a.getEffectiveFrom(), a.getEffectiveTo(), a.getStatus(), a.getCreatedAt(),
                a.getCreatedBy(), a.getUpdatedAt(), a.getUpdatedBy(),
                a.getRowVersion() == null ? 1 : a.getRowVersion());
    }
}
