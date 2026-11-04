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

/**
 * AUTH_DEVICES_MANAGE (Sign-in epic, Scope: Self): "see own active devices and log them out."
 * See SessionResponse's class comment for why this is the single-current-session slice (Phase 1)
 * rather than a real multi-device list (Phase 2, not buildable today without new Keycloak admin
 * read calls and/or a device-tracking table this app does not have).
 *
 * Same resolution pattern as ProfileController/AuthController.me(): principal from the verified
 * Authentication, never a path parameter - "own" sessions only.
 */
@RestController
@RequestMapping("/api/me/sessions")
@RequiredArgsConstructor
@Tag(name = "Self-service", description = "See your own active session(s) and log them out")
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    @Operation(summary = "List the session(s) belonging to the authenticated principal "
            + "(today: just the current one - see SessionResponse)")
    public ResponseEntity<List<SessionResponse>> listSessions(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Jwt jwt = (Jwt) authentication.getCredentials();
        return ResponseEntity.ok(sessionService.listCurrentSessions(jwt));
    }

    @DeleteMapping("/current")
    @Operation(summary = "Revoke the session the caller is currently making this request with")
    public ResponseEntity<Void> revokeCurrentSession(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Jwt jwt = (Jwt) authentication.getCredentials();
        switch (authentication.getPrincipal()) {
            case TenantPrincipal tenant -> sessionService.revokeCurrentSession(tenant, jwt);
            case PlatformPrincipal platform -> sessionService.revokeCurrentSession(platform, jwt);
            case null, default -> {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }
        return ResponseEntity.noContent().build();
    }
}
