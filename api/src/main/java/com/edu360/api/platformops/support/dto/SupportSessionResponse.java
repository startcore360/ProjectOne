package com.edu360.api.platformops.support.dto;

import java.time.Instant;

public record SupportSessionResponse(String token, Instant expiresAt, String tenantLoginAddress) {
}
