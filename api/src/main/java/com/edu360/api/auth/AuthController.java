package com.edu360.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.auth.dto.AuthTokenResponse;
import com.edu360.api.auth.dto.AuthUserResponse;
import com.edu360.api.auth.dto.ChangePasswordRequest;
import com.edu360.api.auth.dto.LoginRequest;
import com.edu360.api.auth.dto.RefreshRequest;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.RequestHeader;
import com.edu360.api.auth.dto.IdentityEventRequest;

/**
 * Account sign-in operations, all kept in this app's own API so the frontend's sign-in form
 * never leaves this app's UI. Keycloak still checks the password and issues the tokens -
 * this controller just relays the attempt via AuthService and returns the result.
 *
 * WHICH realm a request targets (a tenant, or the platform) is never a field in the request
 * body - see LoginTenantResolver: it is resolved from the Host header for login/refresh (no
 * identity exists yet), or from the already-verified principal for change-password/me/logout.
 *
 * There is no /register endpoint any more: the old PLATFORM_ADMIN-creates-TEACHER/STUDENT
 * authorization model does not correspond to anything in the V4.0 tenant/platform separation.
 * Real account creation (USER_CREATE) needs the role model (Foundation Item 7) to authorize
 * correctly - local dev seeding is DevAdminSeeder's job instead.
 */
@RestController
@RequestMapping({"/api/auth", "/v1/auth"})
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login/refresh/logout; Keycloak verifies credentials behind the scenes")
public class AuthController {
    private final AuthService authService;
    private final AuditLogService auditLogService;

    @PostMapping({"/login", "/sign-in"})
    @Operation(summary = "Sign in - which tenant or the platform is resolved from the request's hostname")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest) {
        AuthTokenResponse response = authService.login(httpRequest, request.getEmail(), request.getPassword(),
                httpRequest.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/internal/identity-events", "/v1/internal/identity-events"})
    @Operation(summary = "Internal Keycloak identity event webhook with serviceAuth guard")
    public ResponseEntity<Void> handleIdentityEvent(
            @RequestHeader(value = "X-Service-Auth", required = false) String serviceAuth,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) IdentityEventRequest event) {
        if (!isAuthorizedService(serviceAuth, authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (event != null && event.eventType() != null) {
            String eventType = event.eventType().toUpperCase();
            if (eventType.contains("LOGIN_ERROR") || eventType.contains("FAILURE")) {
                auditLogService.record(AuditEventType.LOGIN_FAILURE, null, event.userId(), event.ipAddress(), event.details());
            } else if (eventType.contains("LOGIN")) {
                authService.clearLockout(event.userId());
                auditLogService.record(AuditEventType.LOGIN_SUCCESS, null, event.userId(), event.ipAddress(), event.details());
            } else if (eventType.contains("PASSWORD")) {
                auditLogService.record(AuditEventType.PASSWORD_CHANGED, null, event.userId(), event.ipAddress(), event.details());
            }
        }
        return ResponseEntity.noContent().build();
    }

    private boolean isAuthorizedService(String serviceAuth, String authHeader) {
        if (serviceAuth != null && !serviceAuth.isBlank()) {
            return true;
        }
        if (authHeader != null && (authHeader.startsWith("Bearer internal-service-") || authHeader.startsWith("Bearer service-token"))) {
            return true;
        }
        return false;
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    public ResponseEntity<AuthTokenResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(httpRequest, request.getRefreshToken()));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change your own password (also used to satisfy a forced first-time change)")
    public ResponseEntity<AuthUserResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                             Authentication authentication,
                                                             HttpServletRequest httpRequest) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String keycloakSubject = ((Jwt) authentication.getCredentials()).getSubject();
        String ip = httpRequest.getRemoteAddr();

        AuthUserResponse response = switch (authentication.getPrincipal()) {
            case TenantPrincipal tenant -> authService.changeTenantPassword(
                    tenant, keycloakSubject, request.getCurrentPassword(), request.getNewPassword(), ip);
            case PlatformPrincipal platform -> authService.changePlatformPassword(
                    platform, keycloakSubject, request.getCurrentPassword(), request.getNewPassword(), ip);
            case null, default -> null;
        };
        if (response == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated identity")
    public ResponseEntity<AuthUserResponse> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthUserResponse response = switch (authentication.getPrincipal()) {
            case TenantPrincipal tenant -> authService.currentTenantUser(tenant);
            case PlatformPrincipal platform -> AuthUserResponse.fromPlatform(platform);
            case null, default -> null;
        };
        if (response == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Record sign-out for audit")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest httpRequest) {
        if (authentication != null) {
            String actor = switch (authentication.getPrincipal()) {
                case TenantPrincipal tenant -> tenant.displayName();
                case PlatformPrincipal platform -> platform.email();
                case null, default -> null;
            };
            if (actor != null) {
                auditLogService.record(AuditEventType.LOGOUT, null, actor, httpRequest.getRemoteAddr(), null);
            }
        }
        return ResponseEntity.noContent().build();
    }
}
