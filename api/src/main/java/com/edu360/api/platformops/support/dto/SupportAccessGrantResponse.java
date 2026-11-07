package com.edu360.api.platformops.support.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.platformops.support.SupportAccessGrant;

public record SupportAccessGrantResponse(
        UUID supportAccessGrantId,
        UUID ticketId,
        UUID tenantId,
        UUID grantedByUserId,
        UUID institutionId,
        Instant grantedAt,
        Instant expiresAt,
        Instant withdrawnAt,
        Integer rowVersion
) {
    public static SupportAccessGrantResponse fromEntity(SupportAccessGrant g) {
        return new SupportAccessGrantResponse(
                g.getSupportAccessGrantId(),
                g.getTicketId(),
                g.getTenantId(),
                g.getGrantedByUserId(),
                g.getInstitutionId(),
                g.getGrantedAt(),
                g.getExpiresAt(),
                g.getWithdrawnAt(),
                g.getRowVersion()
        );
    }
}
