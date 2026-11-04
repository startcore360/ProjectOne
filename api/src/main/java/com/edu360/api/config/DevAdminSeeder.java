package com.edu360.api.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.platform.PlatformUserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Automatically creates one default PLATFORM_ADMIN account when the app starts, but only for
 * local development. Dev-only bootstrap: without this, a fresh database has no platform
 * identity at all to sign in with. Never runs outside the dev profile; production must
 * provision its first platform admin through a real process.
 *
 * Scope note: this seeds ONLY the platform realm/platform_user row, same as before the V4.0
 * identity migration - it does not seed any tenant or tenant user. Seeding a usable demo tenant
 * (platform.tenant + app.tenant_local + a linked app.user_account) is real additional scope
 * belonging to the Platform epic's TENANT_CREATE story, not this migration.
 *
 * WHY the insert below uses a raw JDBC connection as edu_platform instead of
 * PlatformUserRepository.save(...) through the app's normal edu_app-backed datasource: found by
 * actually starting the app (not guessed) - edu_app has zero write access to any platform.*
 * table, by the schema's own design (platform.* writes belong to edu_platform, which
 * sql/03_bindings.sql already grants SELECT/INSERT/UPDATE on platform.platform_user - see
 * docker/postgres/provision-edu-app.ps1). Creating a platform user is exactly the kind of
 * platform-admin-tool operation that role exists for; widening edu_app's own prod-relevant
 * grants just to make a dev convenience seeder work would have been the wrong fix.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevAdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeeder.class);
    private static final String DEFAULT_EMAIL = "admin@edu360-platform.local";
    private static final String DEFAULT_NAME = "Platform Admin";
    private static final String DEFAULT_PASSWORD = "ChangeMe123!";

    private final PlatformUserRepository platformUserRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakProperties keycloakProperties;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${app.platform-admin-datasource.username:edu_platform}")
    private String platformAdminUsername;

    @Value("${app.platform-admin-datasource.password:}")
    private String platformAdminPassword;

    @Override
    public void run(String... args) throws Exception {
        if (platformUserRepository.findByEmail(DEFAULT_EMAIL).isPresent()) {
            return;
        }

        String realm = keycloakProperties.getPlatformRealm();
        // Unlike a real admin-created account, this dev convenience login should not force a
        // password change - it is never a real account.
        String keycloakSubject = keycloakAdminClient.provisionUser(
                realm, DEFAULT_NAME, DEFAULT_EMAIL, DEFAULT_PASSWORD, false);

        UUID platformUserId = UUID.randomUUID();
        insertPlatformUser(platformUserId, keycloakSubject);

        log.warn("Seeded default dev PLATFORM_ADMIN account in realm '{}' -> email: {}, password: {}. "
                + "Dev only; this account must never exist in a deployed environment.",
                realm, DEFAULT_EMAIL, DEFAULT_PASSWORD);
    }

    private void insertPlatformUser(UUID platformUserId, String keycloakSubject) throws Exception {
        String sql = "INSERT INTO platform.platform_user "
                + "(platform_user_id, email, full_name, platform_role, status, mfa_required, "
                + "identity_subject, created_by, updated_by) "
                + "VALUES (?, ?, ?, 'PLATFORM_ADMIN', 'ACTIVE', true, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, platformAdminUsername, platformAdminPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, platformUserId);
            ps.setString(2, DEFAULT_EMAIL);
            ps.setString(3, DEFAULT_NAME);
            ps.setString(4, keycloakSubject);
            ps.setObject(5, platformUserId);
            ps.setObject(6, platformUserId);
            ps.executeUpdate();
        }
    }
}
