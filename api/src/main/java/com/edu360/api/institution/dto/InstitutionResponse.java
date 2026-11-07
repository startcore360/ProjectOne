package com.edu360.api.institution.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.institution.Institution;

public record InstitutionResponse(
        UUID tenantId,
        UUID institutionId,
        String institutionType,
        String institutionCode,
        String institutionName,
        String status,
        boolean externalUniversityMode,
        boolean underUniversity,
        String addressLine,
        String city,
        String stateName,
        String postalCode,
        String principalInviteEmail,
        Instant activatedAt,
        Instant blockedAt,
        Instant createdAt,
        Instant updatedAt,
        int rowVersion
) {
    public static InstitutionResponse fromEntity(Institution institution) {
        return new InstitutionResponse(
                institution.getTenantId(),
                institution.getInstitutionId(),
                institution.getInstitutionType(),
                institution.getInstitutionCode(),
                institution.getInstitutionName(),
                institution.getStatus(),
                institution.isExternalUniversityMode(),
                institution.isUnderUniversity(),
                institution.getAddressLine(),
                institution.getCity(),
                institution.getStateName(),
                institution.getPostalCode(),
                institution.getPrincipalInviteEmail(),
                institution.getActivatedAt(),
                institution.getBlockedAt(),
                institution.getCreatedAt(),
                institution.getUpdatedAt(),
                institution.getRowVersion() != null ? institution.getRowVersion() : 1
        );
    }
}
