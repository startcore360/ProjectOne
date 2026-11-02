package com.edu360.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import com.edu360.api.auth.dto.RegisterRequest;
import com.edu360.api.user.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Account operations plus login/refresh, all kept in this app's own API so the frontend's
 * sign-in form never leaves this app's UI. Keycloak still checks the password and issues the
 * tokens (OD-80) - this controller just relays the attempt via AuthService and returns the
 * result, rather than redirecting the browser to Keycloak's hosted login page.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login/refresh/logout; Keycloak verifies credentials behind the scenes")
public class AuthController {
    private final AuthService authService;
    private final AuditLogService auditLogService;

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest) {
        AuthTokenResponse response = authService.login(request.getEmail(), request.getPassword(),
                httpRequest.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    public ResponseEntity<AuthTokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change your own password (also used to satisfy a forced first-time change)")
    public ResponseEntity<AuthUserResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                             Authentication authentication,
                                                             HttpServletRequest httpRequest) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthUserResponse response = authService.changePassword(user, request.getCurrentPassword(),
                request.getNewPassword(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Create a TEACHER or STUDENT account (platform admin only)")
    public ResponseEntity<AuthUserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                       Authentication authentication,
                                                       HttpServletRequest httpRequest) {
        Long createdByUserId = authentication.getPrincipal() instanceof User creator ? creator.getId() : null;
        User created = authService.register(request, createdByUserId, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthUserResponse.from(created));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user")
    public ResponseEntity<AuthUserResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(AuthUserResponse.from(user));
    }

    @PostMapping("/logout")
    @Operation(summary = "Record sign-out for audit")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest httpRequest) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            auditLogService.record(AuditEventType.LOGOUT, user.getId(), user.getEmail(),
                    httpRequest.getRemoteAddr(), null);
        }
        return ResponseEntity.noContent().build();
    }
}
