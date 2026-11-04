package com.edu360.api.selfservice.dto;

import java.time.Instant;

import lombok.Builder;

/**
 * AUTH_DEVICES_MANAGE (Sign-in epic, Scope: Self): "see own active devices and log them out."
 *
 * PHASE-1 SCOPE, NOT THE FULL STORY: this app has no device/session tracking table of its own
 * (grepped for "session"/"device" across api/src/main/java - nothing exists beyond this). What
 * Keycloak's token actually gives this server, per request, is the ONE session the current
 * access token belongs to (its "sid" claim) - never a list of a user's other sessions on other
 * devices, because that would need the Keycloak admin session-list endpoint cross-referenced
 * with device/user-agent metadata this app does not capture anywhere today. Building a real
 * multi-device list is Phase 2 (needs either Keycloak's GET /admin/realms/{realm}/users/{id}/
 * sessions surfaced through a new admin-read call, or this app's own session/device table) -
 * deliberately not invented here as a fake list.
 *
 * sessionId is null (and revocable=false) when the current access token carries no "sid" claim
 * at all - seen with some direct-grant tokens depending on realm settings - in which case there
 * is nothing this server can ask Keycloak to revoke.
 */
@Builder
public record SessionResponse(
        String sessionId,
        boolean current,
        boolean revocable,
        Instant issuedAt,
        Instant expiresAt) {
}
