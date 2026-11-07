package com.edu360.api.approval.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CreateDelegationRequest(
        @NotNull UUID delegateUserId,
        @NotNull String delegationBasis,
        String processCode,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {
}
