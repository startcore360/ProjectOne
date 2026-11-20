package com.edu360.api.institution;

import java.time.Instant;
import java.util.UUID;

public record StaffDocumentResponse(
        UUID id,
        UUID staffProfileId,
        String documentType,
        UUID fileId,
        String verificationStatus,
        UUID verifiedBy,
        Instant verifiedAt,
        Instant createdAt,
        UUID createdBy,
        Instant updatedAt,
        UUID updatedBy,
        Integer version
) {
    public static StaffDocumentResponse from(StaffDocument document) {
        return new StaffDocumentResponse(
                document.getId().getStaffDocumentId(),
                document.getStaffProfileId(),
                document.getDocumentType(),
                document.getFileId(),
                document.getVerificationStatus(),
                document.getVerifiedBy(),
                document.getVerifiedAt(),
                document.getCreatedAt(),
                document.getCreatedBy(),
                document.getUpdatedAt(),
                document.getUpdatedBy(),
                document.getRowVersion());
    }
}
