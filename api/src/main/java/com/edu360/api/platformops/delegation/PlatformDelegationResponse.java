package com.edu360.api.platformops.delegation;

import java.time.LocalDate;
import java.util.UUID;

/** Exposes the persisted identity, participants, dates, and status of a platform delegation. */
public record PlatformDelegationResponse(
        UUID delegationId,
        UUID delegatorUserId,
        UUID delegateUserId,
        LocalDate from,
        LocalDate to,
        String status) {

    static PlatformDelegationResponse from(PlatformDelegation delegation) {
        return new PlatformDelegationResponse(
                delegation.getDelegationId(),
                delegation.getDelegatorUserId(),
                delegation.getDelegateUserId(),
                delegation.getFromDate(),
                delegation.getToDate(),
                delegation.getStatus());
    }
}
