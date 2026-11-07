package com.edu360.api.selfservice;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.selfservice.dto.SessionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

/**
 * AUTH_DEVICES_MANAGE (Sign-in epic, Scope: Self): "see own active devices and log them out."
 * Maps both /api/me/sessions and D5 OpenAPI /v1/tenants/{tenantId}/me/devices.
 */
@RestController
@RequestMapping({"/api/me/sessions", "/v1/tenants/{tenantId}/me/devices", "/api/tenants/{tenantId}/me/devices"})
@RequiredArgsConstructor
@Tag(name = "Self-service", description = "See your own active session(s) and log them out")
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    @Operation(summary = "List active devices/sessions for the authenticated principal")
    public ResponseEntity<List<SessionResponse>> listSessions(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Jwt jwt = (Jwt) authentication.getCredentials();
        return ResponseEntity.ok(sessionService.listCurrentSessions(jwt));
    }

    @DeleteMapping({"/current"})
    @Operation(summary = "Revoke current session")
    public ResponseEntity<Void> revokeCurrentSession(Authentication authentication) {
        return revokeSession(null, authentication);
    }

    @DeleteMapping({"/{sessionId}"})
    @Operation(summary = "Revoke session by ID")
    public ResponseEntity<Void> revokeSession(@PathVariable(required = false) String sessionId,
                                              Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Jwt jwt = (Jwt) authentication.getCredentials();
        String targetSessionId = (sessionId != null && !sessionId.isBlank() && !"current".equalsIgnoreCase(sessionId))
                ? sessionId
                : null;

        switch (authentication.getPrincipal()) {
            case TenantPrincipal tenant -> {
                if (targetSessionId != null) {
                    sessionService.revokeSession(tenant, targetSessionId);
                } else {
                    sessionService.revokeCurrentSession(tenant, jwt);
                }
            }
            case PlatformPrincipal platform -> {
                if (targetSessionId != null) {
                    sessionService.revokeSession(platform, targetSessionId);
                } else {
                    sessionService.revokeCurrentSession(platform, jwt);
                }
            }
            case null, default -> {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/{sessionId}/revocation", "/revocation"})
    @Operation(summary = "D5 operation: POST .../me/devices/{id}/revocation")
    public ResponseEntity<Void> revokeDeviceD5(@PathVariable(required = false) String sessionId,
                                               Authentication authentication) {
        return revokeSession(sessionId, authentication);
    }

    @PostMapping({"/push-subscriptions", "/me/push-subscriptions"})
    @Operation(summary = "D5 operation: POST .../me/push-subscriptions")
    public ResponseEntity<Void> registerPushSubscription(@RequestBody(required = false) Map<String, Object> body,
                                                         Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (authentication.getPrincipal() instanceof TenantPrincipal tenant) {
            sessionService.registerPushSubscription(tenant, body != null ? body.toString() : "endpoint");
        }
        return ResponseEntity.noContent().build();
    }
}
