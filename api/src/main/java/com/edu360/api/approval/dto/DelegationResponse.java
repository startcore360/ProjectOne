package com.edu360.api.approval.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.approval.Delegation;

public record DelegationResponse(
        UUID delegationId,
        UUID tenantId,
        UUID delegatorUserId,
        UUID delegateUserId,
        String delegationBasis,
        String processCode,
        Instant startsAt,
        Instant endsAt,
        String status,
        Instant cancelledAt,
        UUID cancelledBy,
        Integer rowVersion
) {
    public static DelegationResponse from(Delegation d) {
        return new DelegationResponse(
                d.getDelegationId(),
                d.getTenantId(),
                d.getDelegatorUserId(),
                d.getDelegateUserId(),
                d.getDelegationBasis(),
                d.getProcessCode(),
                d.getStartsAt(),
                d.getEndsAt(),
                d.getStatus(),
                d.getCancelledAt(),
                d.getCancelledBy(),
                d.getRowVersion()
        );
    }
}
