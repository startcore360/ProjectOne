package com.edu360.api.auth;

import org.springframework.stereotype.Service;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.auth.dto.AuthTokenResponse;
import com.edu360.api.auth.dto.AuthUserResponse;
import com.edu360.api.auth.dto.RegisterRequest;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakAuthClient;
import com.edu360.api.exception.InvalidCredentialsException;
import com.edu360.api.user.Role;
import com.edu360.api.user.User;
import com.edu360.api.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Does the real work behind accounts and sign-in: creating accounts by provisioning the
 * matching Keycloak identity (OD-80), linking it to a local User row by Keycloak's user id
 * (OD-10), forwarding login/refresh attempts to Keycloak without ever storing the password,
 * and writing down what happened in the audit log. Keycloak itself — never this service —
 * checks the password; this class only relays the attempt and records the outcome.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakAuthClient keycloakAuthClient;
    private final AuditLogService auditLogService;

    public User register(RegisterRequest request, Long createdByUserId, String ipAddress) {
        // Nobody can create a new PLATFORM_ADMIN through the API, including an existing
        // platform admin. That role only ever comes from the dev seeder (DevAdminSeeder) or
        // a real production setup step - never from a normal request.
        if (request.getRole() == Role.PLATFORM_ADMIN) {
            throw new IllegalArgumentException("Platform admin accounts cannot be created through this endpoint");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        String keycloakId = keycloakAdminClient.provisionUser(
                request.getName(), request.getEmail(), request.getPassword(), request.getRole().name());

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .keycloakId(keycloakId)
                .role(request.getRole())
                .build();

        user = userRepository.save(user);

        auditLogService.record(AuditEventType.ACCOUNT_CREATED, user.getId(), user.getEmail(), ipAddress,
                "Created by user " + createdByUserId);

        return user;
    }

    public AuthTokenResponse login(String email, String password, String ipAddress) {
        KeycloakAuthClient.TokenResult tokens;
        try {
            tokens = keycloakAuthClient.login(email, password);
        } catch (InvalidCredentialsException ex) {
            auditLogService.record(AuditEventType.LOGIN_FAILURE, null, email, ipAddress, null);
            throw ex;
        }

        User user = userRepository.findByKeycloakId(tokens.keycloakId())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        auditLogService.record(AuditEventType.LOGIN_SUCCESS, user.getId(), user.getEmail(), ipAddress, null);

        return toResponse(tokens, user);
    }

    public AuthTokenResponse refresh(String refreshToken) {
        KeycloakAuthClient.TokenResult tokens = keycloakAuthClient.refresh(refreshToken);
        User user = userRepository.findByKeycloakId(tokens.keycloakId())
                .orElseThrow(() -> new InvalidCredentialsException("Session is no longer valid"));
        return toResponse(tokens, user);
    }

    /** Used both for the forced first-time change and a later voluntary one - the current
     *  password is re-verified against Keycloak itself (not trusted from the bearer token
     *  alone) before the new one is accepted. */
    public AuthUserResponse changePassword(User user, String currentPassword, String newPassword,
                                            String ipAddress) {
        keycloakAuthClient.login(user.getEmail(), currentPassword);

        keycloakAdminClient.resetPassword(user.getKeycloakId(), newPassword);

        user.setMustChangePassword(false);
        user = userRepository.save(user);

        auditLogService.record(AuditEventType.PASSWORD_CHANGED, user.getId(), user.getEmail(), ipAddress, null);

        return AuthUserResponse.from(user);
    }

    private AuthTokenResponse toResponse(KeycloakAuthClient.TokenResult tokens, User user) {
        return AuthTokenResponse.builder()
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .expiresInSeconds(tokens.expiresInSeconds())
                .user(AuthUserResponse.from(user))
                .build();
    }
}
