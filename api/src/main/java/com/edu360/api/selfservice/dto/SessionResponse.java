package com.edu360.api.selfservice.dto;

import java.time.Instant;

import lombok.Builder;

/**
 * AUTH_DEVICES_MANAGE (Sign-in epic, Scope: Self): "see own active devices and log them out."
 *
 * One entry per live Keycloak session of the caller (SES-01: any number of devices, one session
 * per browser). Keycloak decides which sessions exist; the device fields come from the row this
 * app recorded at sign-in (app.user_device / platform.platform_user_device).
 *
 * - current: this is the session the request itself was made with.
 * - issuedAt: when that device signed in. lastActiveAt: Keycloak's last activity time.
 * - expiresAt: the current access token's expiry; null for other sessions.
 * - deviceType: PHONE, COMPUTER, or UNKNOWN for a session signed in before device recording
 *   existed (then deviceName/ipAddress are null too).
 * - revocable: false only when no session id is known (a token without a "sid" claim).
 */
@Builder
public record SessionResponse(
        String sessionId,
        boolean current,
        boolean revocable,
        Instant issuedAt,
        Instant expiresAt,
        String deviceType,
        String deviceName,
        String ipAddress,
        Instant lastActiveAt) {

    public SessionResponse(String sessionId, boolean current, boolean revocable, Instant issuedAt, Instant expiresAt) {
        this(sessionId, current, revocable, issuedAt, expiresAt, "UNKNOWN", null, null, null);
    }
}
