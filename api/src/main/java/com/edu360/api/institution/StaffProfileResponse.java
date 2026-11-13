package com.edu360.api.institution;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StaffProfileResponse(
        UUID id, UUID userAccountId, String staffCode, String fullName,
        LocalDate dateOfBirth, String personalEmail, String personalMobile,
        String nationalIdLast4, UUID photoFileId, String employmentStatus,
        LocalDate joinedOn, LocalDate leftOn, Instant createdAt, UUID createdBy,
        Instant updatedAt, UUID updatedBy, Integer version
) {
    /**
     * Safe default for read-only callers: personal contact and identity fields are withheld.
     * Callers must explicitly establish STAFF_RECORD_MANAGE before requesting the full view.
     */
    public static StaffProfileResponse from(StaffProfile p) {
        return from(p, false);
    }

    public static StaffProfileResponse from(StaffProfile p, boolean includeSensitive) {
        return new StaffProfileResponse(p.getStaffProfileId(), p.getUserAccountId(), p.getStaffCode(),
                p.getFullName(), includeSensitive ? p.getDateOfBirth() : null,
                includeSensitive ? p.getPersonalEmail() : null,
                includeSensitive ? p.getPersonalMobile() : null,
                includeSensitive ? p.getNationalIdLast4() : null,
                p.getPhotoFileId(), p.getEmploymentStatus(), p.getJoinedOn(), p.getLeftOn(),
                p.getCreatedAt(), p.getCreatedBy(), p.getUpdatedAt(), p.getUpdatedBy(),
                p.getRowVersion() == null ? 1 : p.getRowVersion());
    }
}
