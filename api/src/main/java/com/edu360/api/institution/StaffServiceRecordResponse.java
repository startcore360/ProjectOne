package com.edu360.api.institution;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StaffServiceRecordResponse(
        UUID id, UUID staffProfileId, String eventType, LocalDate effectiveOn,
        String details, UUID staffAssignmentId, UUID documentFileId,
        Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy, Integer version
) {
    public static StaffServiceRecordResponse from(StaffServiceRecord r) {
        return new StaffServiceRecordResponse(r.getId().getStaffServiceRecordId(),
                r.getStaffProfileId(), r.getEventType(), r.getEffectiveOn(), r.getDetails(),
                r.getStaffAssignmentId(), r.getDocumentFileId(), r.getCreatedAt(),
                r.getCreatedBy(), r.getUpdatedAt(), r.getUpdatedBy(),
                r.getRowVersion() == null ? 1 : r.getRowVersion());
    }
}
