package com.edu360.api.auth.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Payload for D5 POST /v1/internal/identity-events.
 * Relayed from Keycloak event listeners or internal identity webhooks.
 */
public record IdentityEventRequest(
        String eventType,
        String realm,
        String userId,
        String ipAddress,
        String details,
        Map<String, String> context,
        Instant timestamp) {
}
