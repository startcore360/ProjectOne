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
    public static StaffProfileResponse from(StaffProfile p) {
        return new StaffProfileResponse(p.getStaffProfileId(), p.getUserAccountId(), p.getStaffCode(),
                p.getFullName(), p.getDateOfBirth(), p.getPersonalEmail(), p.getPersonalMobile(),
                p.getNationalIdLast4(), p.getPhotoFileId(), p.getEmploymentStatus(), p.getJoinedOn(),
                p.getLeftOn(), p.getCreatedAt(), p.getCreatedBy(), p.getUpdatedAt(), p.getUpdatedBy(),
                p.getRowVersion() == null ? 1 : p.getRowVersion());
    }
}
