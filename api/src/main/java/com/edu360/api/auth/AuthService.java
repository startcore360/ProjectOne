package com.edu360.api.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.auth.dto.AuthResponse;
import com.edu360.api.auth.dto.LoginRequest;
import com.edu360.api.auth.dto.RegisterRequest;
import com.edu360.api.config.JwtUtil;
import com.edu360.api.exception.AccountLockedException;
import com.edu360.api.user.Role;
import com.edu360.api.user.User;
import com.edu360.api.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Does the real work behind signing in and creating accounts: checking the
 * password, counting failed attempts, locking an account after too many
 * wrong guesses, and writing down what happened in the audit log.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    // Security policy: lock an account after 5 failed attempts for 15 minutes.
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditLogService auditLogService;

    public AuthResponse register(RegisterRequest request, Long createdByUserId, String ipAddress) {
        // Nobody can create a new PLATFORM_ADMIN through the API, including an existing
        // platform admin. That role only ever comes from the dev seeder (DevAdminSeeder) or
        // a real production setup step - never from a normal request.
        if (request.getRole() == Role.PLATFORM_ADMIN) {
            throw new IllegalArgumentException("Platform admin accounts cannot be created through this endpoint");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        user = userRepository.save(user);

        auditLogService.record(AuditEventType.ACCOUNT_CREATED, user.getId(), user.getEmail(), ipAddress,
                "Created by user " + createdByUserId);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    public AuthResponse login(LoginRequest request, String ipAddress) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            auditLogService.record(AuditEventType.LOGIN_FAILURE, null, request.getEmail(), ipAddress,
                    "No account for identifier");
            throw new IllegalArgumentException("Invalid email or password");
        }

        Instant now = Instant.now();
        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(now)) {
                auditLogService.record(AuditEventType.LOGIN_FAILURE, user.getId(), user.getEmail(), ipAddress,
                        "Attempt while locked");
                throw new AccountLockedException(
                        "Too many failed attempts. Try again after " + user.getLockedUntil() + ".");
            }
            // Lock window has passed; reset before evaluating this attempt.
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        // Wrong password: bump the failed-attempt counter, and lock the account once it
        // reaches MAX_FAILED_ATTEMPTS. The counter is reset to 0 on the next successful
        // login (below) or once the lock window above has passed.
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(now.plus(LOCKOUT_DURATION));
                userRepository.save(user);
                auditLogService.record(AuditEventType.ACCOUNT_LOCKED, user.getId(), user.getEmail(), ipAddress,
                        "Locked after " + attempts + " failed attempts");
                throw new AccountLockedException("Too many failed attempts. Account locked for 15 minutes.");
            }
            userRepository.save(user);
            auditLogService.record(AuditEventType.LOGIN_FAILURE, user.getId(), user.getEmail(), ipAddress,
                    "Failed attempt " + attempts + " of " + MAX_FAILED_ATTEMPTS);
            throw new IllegalArgumentException("Invalid email or password");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        auditLogService.record(AuditEventType.LOGIN_SUCCESS, user.getId(), user.getEmail(), ipAddress, null);

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}
