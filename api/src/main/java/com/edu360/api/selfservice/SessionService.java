package com.edu360.api.selfservice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakAdminClient.UserSessionInfo;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.platformops.session.PlatformUserDevice;
import com.edu360.api.platformops.session.PlatformUserDeviceRepository;
import com.edu360.api.selfservice.dto.SessionResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

import lombok.RequiredArgsConstructor;

/**
 * AUTH_DEVICES_MANAGE (Sign-in epic, Scope: Self): "see own active devices and log them out."
 *
 * SES-01 (docs/GOVERNANCE_DECISION_LOG.md, 2026-11-09) replaces R2-11's "one phone and one
 * computer": a user may be signed in on any number of devices, and signing in again from the
 * SAME browser replaces that browser's previous session.
 *
 * WHO KNOWS WHAT:
 * - Keycloak is the authority on which sessions are alive (listUserSessions). Expired or
 *   signed-out sessions are simply absent there; matching rows here are then marked EXPIRED.
 * - This app records, at sign-in, what Keycloak cannot know with a server-side password grant:
 *   device class, a readable label, the caller's real IP, and a hash of the browser id. Tenant
 *   users go to app.user_device (RLS, edu_app); platform staff to platform.platform_user_device
 *   (edu_platform) - the same split as every other tenant/platform table.
 *
 * OWNERSHIP: a session id from the client is only ever acted on after checking it is one of the
 * caller's own live Keycloak sessions (listed by the caller's own token subject). Anything else
 * is a 404, never a cross-user sign-out.
 *
 * Recording a device is best effort: a failure there is logged and never blocks a sign-in.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    static final String REASON_NEW_LOGIN = "NEW_LOGIN";
    static final String REASON_USER = "USER";
    static final String REASON_EXPIRED = "EXPIRED";
    static final String UNKNOWN_DEVICE = "UNKNOWN";

    private final TenantRepository tenantRepository;
    private final KeycloakProperties keycloakProperties;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditLogService auditLogService;
    private final UserDeviceRepository userDeviceRepository;
    private final PlatformUserDeviceRepository platformUserDeviceRepository;

    /** What the browser told us at sign-in. */
    public record SignInContext(String sessionId, String browserKey, String userAgent, String ipAddress) {
    }

    // ------------------------------------------------------------------------------ sign-in

    /** Called by AuthService after a successful tenant sign-in, while TenantContext is set. */
    public void recordTenantSignIn(Tenant tenant, UUID userAccountId, SignInContext ctx) {
        if (ctx.sessionId() == null) {
            return;
        }
        try {
            UUID tenantId = tenant.getTenantId();
            String browserHash = DeviceDescriptor.hashBrowserKey(ctx.browserKey());
            if (browserHash != null) {
                userDeviceRepository
                        .findByIdTenantIdAndUserAccountIdAndBrowserKeyHashAndRevokedAtIsNull(tenantId, userAccountId, browserHash)
                        .ifPresent(previous -> {
                            markRevoked(previous, REASON_NEW_LOGIN, userAccountId);
                            endKeycloakSession(tenant.getKeycloakRealm(), previous.getKeycloakSessionId(), ctx.sessionId());
                        });
            }
            Instant now = Instant.now();
            userDeviceRepository.save(UserDevice.builder()
                    .id(new UserDeviceId(tenantId, UUID.randomUUID()))
                    .userAccountId(userAccountId)
                    .deviceClass(DeviceDescriptor.deviceClass(ctx.userAgent()))
                    .deviceLabel(DeviceDescriptor.label(ctx.userAgent()))
                    .keycloakSessionId(ctx.sessionId())
                    .browserKeyHash(browserHash)
                    .ipAddress(trimIp(ctx.ipAddress()))
                    .registeredAt(now)
                    .lastSeenAt(now)
                    .createdBy(userAccountId)
                    .updatedBy(userAccountId)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Could not record signed-in device for tenant user {}: {}", userAccountId, ex.getMessage());
        }
    }

    /** Called by AuthService after a successful platform sign-in. */
    public void recordPlatformSignIn(UUID platformUserId, SignInContext ctx) {
        if (ctx.sessionId() == null) {
            return;
        }
        try {
            String browserHash = DeviceDescriptor.hashBrowserKey(ctx.browserKey());
            if (browserHash != null) {
                platformUserDeviceRepository
                        .findByPlatformUserIdAndBrowserKeyHashAndRevokedAtIsNull(platformUserId, browserHash)
                        .ifPresent(previous -> {
                            markRevoked(previous, REASON_NEW_LOGIN);
                            endKeycloakSession(keycloakProperties.getPlatformRealm(), previous.getKeycloakSessionId(), ctx.sessionId());
                        });
            }
            Instant now = Instant.now();
            platformUserDeviceRepository.save(PlatformUserDevice.builder()
                    .platformUserDeviceId(UUID.randomUUID())
                    .platformUserId(platformUserId)
                    .deviceClass(DeviceDescriptor.deviceClass(ctx.userAgent()))
                    .deviceLabel(DeviceDescriptor.label(ctx.userAgent()))
                    .keycloakSessionId(ctx.sessionId())
                    .browserKeyHash(browserHash)
                    .ipAddress(trimIp(ctx.ipAddress()))
                    .registeredAt(now)
                    .lastSeenAt(now)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Could not record signed-in device for platform user {}: {}", platformUserId, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------------- list

    public List<SessionResponse> listSessions(TenantPrincipal principal, Jwt jwt) {
        String realm = realmOf(principal);
        List<UserSessionInfo> live = liveSessions(realm, jwt);
        if (live == null) {
            return List.of(currentOnly(jwt));
        }
        List<UserDevice> rows = userDeviceRepository
                .findByIdTenantIdAndUserAccountIdAndRevokedAtIsNull(principal.tenantId(), principal.userAccountId());
        Set<String> liveIds = ids(live);
        Map<String, DeviceView> bySession = new HashMap<>();
        for (UserDevice row : rows) {
            if (row.getKeycloakSessionId() == null || !liveIds.contains(row.getKeycloakSessionId())) {
                markRevoked(row, REASON_EXPIRED, principal.userAccountId());
            } else {
                bySession.put(row.getKeycloakSessionId(),
                        new DeviceView(row.getDeviceClass(), row.getDeviceLabel(), row.getIpAddress(), row.getRegisteredAt(),
                                row.getLastSeenAt()));
            }
        }
        return toResponses(live, bySession, jwt);
    }

    public List<SessionResponse> listSessions(PlatformPrincipal principal, Jwt jwt) {
        List<UserSessionInfo> live = liveSessions(keycloakProperties.getPlatformRealm(), jwt);
        if (live == null) {
            return List.of(currentOnly(jwt));
        }
        List<PlatformUserDevice> rows = platformUserDeviceRepository.findByPlatformUserIdAndRevokedAtIsNull(principal.platformUserId());
        Set<String> liveIds = ids(live);
        Map<String, DeviceView> bySession = new HashMap<>();
        for (PlatformUserDevice row : rows) {
            if (!liveIds.contains(row.getKeycloakSessionId())) {
                markRevoked(row, REASON_EXPIRED);
            } else {
                bySession.put(row.getKeycloakSessionId(),
                        new DeviceView(row.getDeviceClass(), row.getDeviceLabel(), row.getIpAddress(), row.getRegisteredAt(),
                                row.getLastSeenAt()));
            }
        }
        return toResponses(live, bySession, jwt);
    }

    // ---------------------------------------------------------------------------- activity

    /**
     * Notes that this session just made an API call (SessionActivityInterceptor, throttled to once
     * a minute per session). "Active now" on other devices comes from this, because Keycloak only
     * sees a device when it renews its sign-in. Best effort: never fails the request.
     */
    public void recordActivity(Object principal, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            Instant now = Instant.now();
            switch (principal) {
                case TenantPrincipal tenant -> userDeviceRepository
                        .findByIdTenantIdAndKeycloakSessionId(tenant.tenantId(), sessionId)
                        .filter(row -> row.getRevokedAt() == null)
                        .ifPresent(row -> {
                            row.setLastSeenAt(now);
                            row.setUpdatedBy(tenant.userAccountId());
                            userDeviceRepository.save(row);
                        });
                case PlatformPrincipal platform -> platformUserDeviceRepository
                        .findByKeycloakSessionId(sessionId)
                        .filter(row -> row.getRevokedAt() == null && platform.platformUserId().equals(row.getPlatformUserId()))
                        .ifPresent(row -> {
                            row.setLastSeenAt(now);
                            platformUserDeviceRepository.save(row);
                        });
                case null, default -> { }
            }
        } catch (RuntimeException ex) {
            log.debug("Could not record session activity: {}", ex.getMessage());
        }
    }

    /**
     * True when this session's device row says it was signed out (from another device, a newer
     * sign-in on the same browser, sign-out, or expiry). Access tokens are verified by signature
     * only, so without this check a signed-out device could keep using its still-unexpired access
     * token for a few minutes. Sessions with no row (signed in before SES-01) are not blocked.
     * On a lookup failure the request is allowed - Keycloak still stops the session renewing.
     */
    public boolean isSessionSignedOut(Object principal, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        try {
            return switch (principal) {
                case TenantPrincipal tenant -> userDeviceRepository
                        .findByIdTenantIdAndKeycloakSessionId(tenant.tenantId(), sessionId)
                        .map(row -> row.getRevokedAt() != null)
                        .orElse(false);
                case PlatformPrincipal platform -> platformUserDeviceRepository
                        .findByKeycloakSessionId(sessionId)
                        .filter(row -> platform.platformUserId().equals(row.getPlatformUserId()))
                        .map(row -> row.getRevokedAt() != null)
                        .orElse(false);
                case null, default -> false;
            };
        } catch (RuntimeException ex) {
            log.debug("Could not check session state: {}", ex.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------------------ revoke

    public void revokeCurrentSession(TenantPrincipal principal, Jwt jwt) {
        String sessionId = requireSessionId(jwt);
        endTenantSession(principal, sessionId, true);
    }

    public void revokeCurrentSession(PlatformPrincipal principal, Jwt jwt) {
        String sessionId = requireSessionId(jwt);
        endPlatformSession(principal, sessionId, true);
    }

    /** Signs out one of the caller's own sessions (any device). 404 if it is not theirs. */
    public void revokeSession(TenantPrincipal principal, String sessionId, Jwt jwt) {
        requireNonBlank(sessionId);
        if (!isOwnSession(realmOf(principal), jwt, sessionId)) {
            throw new ResourceNotFoundException("Session " + sessionId + " not found for this user");
        }
        endTenantSession(principal, sessionId, true);
    }

    public void revokeSession(PlatformPrincipal principal, String sessionId, Jwt jwt) {
        requireNonBlank(sessionId);
        if (!isOwnSession(keycloakProperties.getPlatformRealm(), jwt, sessionId)) {
            throw new ResourceNotFoundException("Session " + sessionId + " not found for this user");
        }
        endPlatformSession(principal, sessionId, true);
    }

    /** POST /api/auth/logout: end this browser's session too, quietly if the token has no sid. */
    public void signOut(Object principal, Jwt jwt) {
        String sessionId = jwt == null ? null : jwt.getClaimAsString("sid");
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            switch (principal) {
                case TenantPrincipal tenant -> endTenantSession(tenant, sessionId, false);
                case PlatformPrincipal platform -> endPlatformSession(platform, sessionId, false);
                default -> { }
            }
        } catch (RuntimeException ex) {
            // Sign-out must always succeed for the caller; the token still expires on its own.
            log.warn("Could not end Keycloak session on sign-out: {}", ex.getMessage());
        }
    }

    public void registerPushSubscription(TenantPrincipal principal, String endpoint) {
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.displayName(), null, "Push subscription registered");
    }

    // ----------------------------------------------------------------------------- helpers

    // audit=false only for /auth/logout, which already writes its own LOGOUT audit entry.
    private void endTenantSession(TenantPrincipal principal, String sessionId, boolean audit) {
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no longer exists"));
        keycloakAdminClient.logoutSession(tenant.getKeycloakRealm(), sessionId);
        userDeviceRepository.findByIdTenantIdAndKeycloakSessionId(principal.tenantId(), sessionId)
                .filter(row -> row.getRevokedAt() == null)
                .ifPresent(row -> markRevoked(row, REASON_USER, principal.userAccountId()));
        if (audit) {
            auditLogService.record(AuditEventType.SESSION_REVOKED, null, principal.displayName(), null, null);
        }
    }

    private void endPlatformSession(PlatformPrincipal principal, String sessionId, boolean audit) {
        keycloakAdminClient.logoutSession(keycloakProperties.getPlatformRealm(), sessionId);
        platformUserDeviceRepository.findByKeycloakSessionId(sessionId)
                .filter(row -> row.getRevokedAt() == null && principal.platformUserId().equals(row.getPlatformUserId()))
                .ifPresent(row -> markRevoked(row, REASON_USER));
        if (audit) {
            auditLogService.record(AuditEventType.SESSION_REVOKED, null, principal.email(), null, null);
        }
    }

    private boolean isOwnSession(String realm, Jwt jwt, String sessionId) {
        if (sessionId.equals(jwt.getClaimAsString("sid"))) {
            return true;
        }
        return keycloakAdminClient.listUserSessions(realm, jwt.getSubject()).stream()
                .anyMatch(s -> sessionId.equals(s.id()));
    }

    /** null = Keycloak could not be asked; callers fall back to the current session only. */
    private List<UserSessionInfo> liveSessions(String realm, Jwt jwt) {
        try {
            return keycloakAdminClient.listUserSessions(realm, jwt.getSubject());
        } catch (RuntimeException ex) {
            log.warn("Could not list Keycloak sessions for realm {}: {}", realm, ex.getMessage());
            return null;
        }
    }

    private List<SessionResponse> toResponses(List<UserSessionInfo> live, Map<String, DeviceView> bySession, Jwt jwt) {
        String currentSid = jwt.getClaimAsString("sid");
        List<SessionResponse> out = new ArrayList<>();
        for (UserSessionInfo session : live) {
            DeviceView device = bySession.get(session.id());
            boolean current = session.id().equals(currentSid);
            Instant started = device != null && device.registeredAt() != null ? device.registeredAt() : epoch(session.start());
            out.add(SessionResponse.builder()
                    .sessionId(session.id())
                    .current(current)
                    .revocable(true)
                    .issuedAt(started)
                    .expiresAt(current ? jwt.getExpiresAt() : null)
                    .deviceType(device != null ? device.deviceClass() : UNKNOWN_DEVICE)
                    .deviceName(device != null ? device.label() : null)
                    .ipAddress(device != null ? device.ipAddress() : null)
                    .lastActiveAt(latest(device != null ? device.lastSeenAt() : null, epoch(session.lastAccess())))
                    .build());
        }
        // This device first, then most recently active.
        out.sort(Comparator.comparing(SessionResponse::current).reversed()
                .thenComparing(SessionResponse::lastActiveAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    /** Fallback when Keycloak's session list is unreachable: show just this session. */
    private SessionResponse currentOnly(Jwt jwt) {
        String sessionId = jwt.getClaimAsString("sid");
        return SessionResponse.builder()
                .sessionId(sessionId)
                .current(true)
                .revocable(sessionId != null && !sessionId.isBlank())
                .issuedAt(jwt.getIssuedAt())
                .expiresAt(jwt.getExpiresAt())
                .deviceType(UNKNOWN_DEVICE)
                .build();
    }

    private void endKeycloakSession(String realm, String oldSessionId, String newSessionId) {
        if (oldSessionId == null || oldSessionId.equals(newSessionId)) {
            return;
        }
        try {
            keycloakAdminClient.logoutSession(realm, oldSessionId);
        } catch (RuntimeException ex) {
            log.warn("Could not end replaced session in realm {}: {}", realm, ex.getMessage());
        }
    }

    private void markRevoked(UserDevice row, String reason, UUID actor) {
        row.setRevokedAt(Instant.now());
        row.setRevokedReason(reason);
        row.setUpdatedBy(actor);
        userDeviceRepository.save(row);
    }

    private void markRevoked(PlatformUserDevice row, String reason) {
        row.setRevokedAt(Instant.now());
        row.setRevokedReason(reason);
        platformUserDeviceRepository.save(row);
    }

    private String realmOf(TenantPrincipal principal) {
        return tenantRepository.findById(principal.tenantId())
                .map(Tenant::getKeycloakRealm)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no longer exists"));
    }

    private static Set<String> ids(List<UserSessionInfo> live) {
        Set<String> ids = new HashSet<>();
        live.forEach(s -> ids.add(s.id()));
        return ids;
    }

    private static Instant latest(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static Instant epoch(Long millis) {
        return millis == null ? null : Instant.ofEpochMilli(millis);
    }

    private static String trimIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        return Optional.of(ip.trim()).filter(v -> v.length() >= 2 && v.length() <= 45).orElse(null);
    }

    private static void requireNonBlank(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID is required for revocation");
        }
    }

    private String requireSessionId(Jwt jwt) {
        String sessionId = jwt.getClaimAsString("sid");
        if (sessionId == null || sessionId.isBlank()) {
            // 400, not 404/500: the caller IS authenticated, the token just carries no "sid".
            throw new IllegalArgumentException(
                    "This session cannot be revoked: the current access token has no session id");
        }
        return sessionId;
    }

    private record DeviceView(String deviceClass, String label, String ipAddress, Instant registeredAt,
                              Instant lastSeenAt) {
    }
}
