package com.edu360.api.guardian.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.guardian.GuardianProfile;
import com.edu360.api.guardian.StudentGuardianLink;

public record GuardianLinkResponse(
        UUID studentGuardianLinkId,
        UUID tenantId,
        UUID studentProfileId,
        UUID guardianProfileId,
        String guardianName,
        String guardianMobile,
        String guardianEmail,
        String relationship,
        boolean isPrimary,
        String status,
        UUID changeApprovalRequestId,
        Instant blockedAt,
        String blockReason,
        Instant createdAt,
        Integer rowVersion
) {
    public static GuardianLinkResponse from(StudentGuardianLink link, GuardianProfile profile) {
        return new GuardianLinkResponse(
                link.getStudentGuardianLinkId(),
                link.getTenantId(),
                link.getStudentProfileId(),
                link.getGuardianProfileId(),
                profile != null ? profile.getFullName() : null,
                profile != null ? profile.getMobile() : null,
                profile != null ? profile.getEmail() : null,
                link.getRelationship(),
                Boolean.TRUE.equals(link.getIsPrimary()),
                link.getStatus(),
                link.getChangeApprovalRequestId(),
                link.getBlockedAt(),
                link.getBlockReason(),
                link.getCreatedAt(),
                link.getRowVersion()
        );
    }
}
