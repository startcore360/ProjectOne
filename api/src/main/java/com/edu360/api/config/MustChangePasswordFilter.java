package com.edu360.api.config;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Server-side enforcement of the forced first-time password change: a frontend redirect alone
 * is not enough, since a bearer token is otherwise valid and would let a stale tab call any
 * other endpoint directly. Blocks every request except the handful needed to actually change
 * the password, log out, or re-check who's signed in, until mustChangePassword clears.
 *
 * WHERE the flag comes from: app.user_account has no such column in the approved V4.0 schema
 * (the schema's own comment says this concern belongs to the identity provider) - so it lives
 * as a Keycloak custom user attribute, read back out as a "must_change_password" JWT claim via
 * each realm's protocol mapper (see docker/keycloak/realm-export-*.json), and already carried on
 * TenantPrincipal/PlatformPrincipal by the authentication converter that verified the token.
 * This filter only ever reads it from the Authentication object - never a fresh Keycloak or
 * database call - since the claim already came from a cryptographically verified token.
 */
@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/auth/me", "/api/auth/change-password", "/api/auth/logout", "/api/auth/session-settings");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // "case null, default" is required, not just "default": Java's pattern-matching switch
        // does NOT let default catch a null selector on its own - found by actually running a
        // real unauthenticated request (e.g. /api/auth/login itself) and hitting a
        // NullPointerException here, not by reading the code.
        boolean mustChangePassword = switch (authentication == null ? null : authentication.getPrincipal()) {
            case TenantPrincipal tenant -> tenant.mustChangePassword();
            case PlatformPrincipal platform -> platform.mustChangePassword();
            case null, default -> false;
        };

        if (mustChangePassword && !ALLOWED_PATHS.contains(request.getRequestURI())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"PASSWORD_CHANGE_REQUIRED\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
