package com.edu360.api.platformops.delegation;

import java.util.UUID;

/** Identifies an active DPO who can receive temporary platform approval authority. */
public record PlatformDelegationCandidate(
        UUID userId,
        String fullName,
        String email) {
}
