package com.edu360.api.selfservice;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SES-01, run on every authenticated API call (after Spring Security, so the principal and the
 * TenantContext needed for the RLS-scoped lookups are already set):
 *
 * 1. SIGNED-OUT CHECK. Access tokens are verified by signature only, so a device that was signed
 *    out from another device (or replaced by a newer sign-in on the same browser) could keep
 *    using its unexpired access token for a few minutes. If the session's device row is
 *    revoked, the call is refused with 401. The SPA then tries to renew, Keycloak refuses (the
 *    session is gone), and the device lands on the login page. Checked on EVERY request, so a
 *    sign-out takes effect on the next call; "signed out" answers are remembered in memory for
 *    good (a session never comes back), so a signed-out device costs no further lookups.
 *
 * 2. ACTIVITY ("Active now" in My sessions). At most one last-seen write per session per minute.
 *
 * Both are in-memory per API instance and never fail a request on their own errors.
 */
@Component
public class SessionActivityInterceptor implements HandlerInterceptor {

    static final long THROTTLE_MS = 60_000;
    private static final int MAX_TRACKED = 20_000;

    private final SessionService sessionService;
    private final Clock clock;
    private final Map<String, Long> lastWrite = new ConcurrentHashMap<>();
    private final Set<String> signedOut = ConcurrentHashMap.newKeySet();

    @Autowired
    public SessionActivityInterceptor(SessionService sessionService) {
        this(sessionService, Clock.systemUTC());
    }

    SessionActivityInterceptor(SessionService sessionService, Clock clock) {
        this.sessionService = sessionService;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getCredentials() instanceof Jwt jwt)) {
            return true;
        }
        String sid = jwt.getClaimAsString("sid");
        if (sid == null || sid.isBlank()) {
            return true;
        }
        long now = clock.millis();

        if (isSignedOut(auth.getPrincipal(), sid)) {
            refuse(response);
            return false;
        }

        Long previous = lastWrite.get(sid);
        if (previous == null || now - previous >= THROTTLE_MS) {
            prune(lastWrite, now, THROTTLE_MS);
            lastWrite.put(sid, now);
            sessionService.recordActivity(auth.getPrincipal(), sid);
        }
        return true;
    }

    /** Asked on every request - no "still active" cache, so a sign-out applies to the very next
     *  call (security requirement). One indexed lookup per request (ux_user_device_session). */
    private boolean isSignedOut(Object principal, String sid) {
        if (signedOut.contains(sid)) {
            return true;
        }
        if (sessionService.isSessionSignedOut(principal, sid)) {
            if (signedOut.size() > MAX_TRACKED) {
                signedOut.clear();
            }
            signedOut.add(sid);
            return true;
        }
        return false;
    }

    private static void prune(Map<String, Long> map, long now, long ttl) {
        if (map.size() > MAX_TRACKED) {
            map.entrySet().removeIf(e -> now - e.getValue() >= ttl);
        }
    }

    /** Same body shape as GlobalExceptionHandler: {timestamp, status, error}. */
    private void refuse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"timestamp\":\"" + Instant.now(clock) + "\",\"status\":401,"
                + "\"error\":\"This session has been signed out. Please sign in again.\"}");
    }
}
