package com.edu360.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.edu360.api.user.Role;
import com.edu360.api.user.User;
import com.edu360.api.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Automatically creates one default PLATFORM_ADMIN account when the app starts,
 * but only for local development. Dev-only bootstrap: PLATFORM_ADMIN is the only
 * role that can create accounts, so a fresh database (dev uses create-drop H2)
 * needs one seeded in to log in at all. Never runs outside the dev profile;
 * production must provision its first platform admin through a real process.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevAdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeeder.class);
    private static final String DEFAULT_EMAIL = "admin@edu360.local";
    private static final String DEFAULT_PASSWORD = "ChangeMe123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(DEFAULT_EMAIL)) {
            return;
        }
        userRepository.save(User.builder()
                .name("Platform Admin")
                .email(DEFAULT_EMAIL)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .role(Role.PLATFORM_ADMIN)
                .build());
        log.warn("Seeded default dev PLATFORM_ADMIN account -> email: {}, password: {}. "
                + "Dev only; this account and password must never exist in a deployed environment.",
                DEFAULT_EMAIL, DEFAULT_PASSWORD);
    }
}
