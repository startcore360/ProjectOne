package com.edu360.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.edu360.api.config.keycloak.KeycloakAdminClient;
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
 *
 * Requires a reachable Keycloak (see docker/keycloak/) — the account now lives
 * there, not in this app, so seeding it means provisioning it through Keycloak's
 * admin API just like AuthService.register does, bypassing only the "nobody can
 * create PLATFORM_ADMIN through the API" rule that register() deliberately enforces.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevAdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeeder.class);
    private static final String DEFAULT_EMAIL = "admin@edu360.local";
    private static final String DEFAULT_NAME = "Platform Admin";
    private static final String DEFAULT_PASSWORD = "ChangeMe123!";

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(DEFAULT_EMAIL)) {
            return;
        }
        String keycloakId = keycloakAdminClient.provisionUser(
                DEFAULT_NAME, DEFAULT_EMAIL, DEFAULT_PASSWORD, Role.PLATFORM_ADMIN.name());

        userRepository.save(User.builder()
                .name(DEFAULT_NAME)
                .email(DEFAULT_EMAIL)
                .keycloakId(keycloakId)
                .role(Role.PLATFORM_ADMIN)
                // Unlike register()-created accounts, this dev convenience login should not
                // force a password change - it is never a real account.
                .mustChangePassword(false)
                .build());
        log.warn("Seeded default dev PLATFORM_ADMIN account in Keycloak -> email: {}, password: {}. "
                + "Dev only; this account must never exist in a deployed environment.",
                DEFAULT_EMAIL, DEFAULT_PASSWORD);
    }
}
