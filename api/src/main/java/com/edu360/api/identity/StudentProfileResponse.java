package com.edu360.api.identity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record StudentProfileResponse(
        UUID id,
        UUID userAccountId,
        UUID institutionId,
        String studentCode,
        String fullName,
        LocalDate dateOfBirth,
        LocalDate admissionDate,
        String nationalIdLast4,
        UUID photoFileId,
        String status,
        String statusReason,
        Instant statusChangedAt,
        Instant createdAt,
        UUID createdBy,
        Integer version,
        @Schema(description = "One-time student password to print or deliver securely; returned only on creation.")
        String temporaryPassword
) {
    public static StudentProfileResponse from(StudentProfile profile) {
        return from(profile, null);
    }

    public static StudentProfileResponse from(StudentProfile profile, String temporaryPassword) {
        return new StudentProfileResponse(profile.getStudentProfileId(), profile.getUserAccountId(),
                profile.getInstitutionId(), profile.getStudentCode(), profile.getFullName(),
                profile.getDateOfBirth(), profile.getAdmissionDate(), profile.getNationalIdLast4(),
                profile.getPhotoFileId(), profile.getStatus(), profile.getStatusReason(),
                profile.getStatusChangedAt(), profile.getCreatedAt(), profile.getCreatedBy(),
                profile.getRowVersion(), temporaryPassword);
    }
}
