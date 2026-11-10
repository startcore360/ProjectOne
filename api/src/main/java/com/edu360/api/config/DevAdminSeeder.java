package com.edu360.api.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Automatically creates one default PLATFORM_ADMIN and one default PRODUCT_OWNER account when the
 * app starts, but only for local development. Dev-only bootstrap: without this, a fresh database
 * has no platform identity at all to sign in with, and nobody who can add platform staff
 * (PLATFORM_USER_MANAGE is Product Owner only). Never runs outside the dev profile; production
 * must provision its first platform users through a real process.
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
    /**
     * B3 / I-06: every realm now refuses common passwords (Keycloak passwordBlacklist and the
     * API's PasswordPolicyService share security/common-passwords.txt), and the old fixed seed
     * password "ChangeMe123!" is on that list - Keycloak refuses to create the account with it.
     * A fresh random password is generated per seeded account instead and printed once in the
     * log line below, as before. Accounts that already exist are never touched.
     */
    private static String newSeedPassword() {
        byte[] bytes = new byte[12];
        new java.security.SecureRandom().nextBytes(bytes);
        return "Dev-" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "-9";
    }
    private static final List<DevAccount> DEFAULT_ACCOUNTS = List.of(
            new DevAccount("admin@edu360-platform.local", "Platform Admin", PlatformRole.PLATFORM_ADMIN),
            new DevAccount("owner@edu360-platform.local", "Product Owner", PlatformRole.PRODUCT_OWNER));

    private record DevAccount(String email, String name, PlatformRole role) {
    }

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
        for (DevAccount account : DEFAULT_ACCOUNTS) {
            if (platformUserRepository.findByEmail(account.email()).isEmpty()) {
                seed(account);
            }
        }
    }

    private void seed(DevAccount account) throws Exception {
        String realm = keycloakProperties.getPlatformRealm();
        // Unlike a real admin-created account, this dev convenience login should not force a
        // password change - it is never a real account.
        String seedPassword = newSeedPassword();
        String keycloakSubject = keycloakAdminClient.provisionUser(
                realm, account.name(), account.email(), seedPassword, false);

        UUID platformUserId = UUID.randomUUID();
        insertPlatformUser(account, platformUserId, keycloakSubject);

        log.warn("Seeded default dev {} account in realm '{}' -> email: {}, password: {}. "
                + "Dev only; this account must never exist in a deployed environment.",
                account.role(), realm, account.email(), seedPassword);
    }

    private void insertPlatformUser(DevAccount account, UUID platformUserId, String keycloakSubject) throws Exception {
        String sql = "INSERT INTO platform.platform_user "
                + "(platform_user_id, email, full_name, platform_role, status, mfa_required, "
                + "identity_subject, created_by, updated_by) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE', true, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, platformAdminUsername, platformAdminPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, platformUserId);
            ps.setString(2, account.email());
            ps.setString(3, account.name());
            ps.setString(4, account.role().name());
            ps.setString(5, keycloakSubject);
            ps.setObject(6, platformUserId);
            ps.setObject(7, platformUserId);
            ps.executeUpdate();
        }
    }
}
