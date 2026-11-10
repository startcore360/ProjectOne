package com.edu360.api.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.platformops.security.SecurityPolicyService;
import com.edu360.api.platformops.security.SecurityPolicyService.SecurityPolicy;

/**
 * WHAT: the API-side password rules (D1 §8.1 R2-12, story 3 AUTH_PASSWORD_CHANGE), read from the
 * saved security policy (story 64 / I-06) so the screen, this API and Keycloak all apply the
 * same values:
 * - minimum length (never below 8 - the policy screen refuses less);
 * - a digit / a special character when the policy asks for them;
 * - not on the common-password list (classpath security/common-passwords.txt - the same file
 *   docker/keycloak/docker-compose.yml mounts into Keycloak as its password denylist), compared
 *   in lower case as Keycloak does;
 * - forced change after the rotation period (120 days by default).
 *
 * The same rules are pushed into each Keycloak realm's passwordPolicy by
 * KeycloakRealmSecurityService, so a password set through Keycloak's own pages is held to them
 * too. This class exists because the API also sets passwords (change-password) and must give a
 * clear 422 rather than Keycloak's generic error.
 */
@Service
public class PasswordPolicyService {

    static final String DENYLIST_RESOURCE = "security/common-passwords.txt";

    private final SecurityPolicyService securityPolicyService;
    private final Set<String> denylist;

    public PasswordPolicyService(SecurityPolicyService securityPolicyService) {
        this.securityPolicyService = securityPolicyService;
        this.denylist = loadDenylist();
    }

    public void validate(String password) {
        SecurityPolicy policy = securityPolicyService.current();
        int min = Math.max(8, policy.minPasswordLength());
        if (password == null || password.length() < min) {
            throw new BusinessRuleViolationException("PASSWORD_TOO_SHORT",
                    "Rule R2-12: Password must be at least " + min + " characters long.");
        }
        if (policy.requireDigit() && password.chars().noneMatch(Character::isDigit)) {
            throw new BusinessRuleViolationException("PASSWORD_NEEDS_DIGIT",
                    "Rule R2-12: Password must contain at least one digit.");
        }
        if (policy.requireSpecialChar() && password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new BusinessRuleViolationException("PASSWORD_NEEDS_SPECIAL",
                    "Rule R2-12: Password must contain at least one special character.");
        }
        if (isCommon(password)) {
            throw new BusinessRuleViolationException("PASSWORD_TOO_COMMON",
                    "Rule R2-12: Common and dictionary passwords are not permitted.");
        }
    }

    public boolean isCommon(String password) {
        return password != null && denylist.contains(password.trim().toLowerCase(Locale.ROOT));
    }

    /** True when a password set at {@code setAt} is older than the policy's rotation period. */
    public boolean isRotationDue(Instant setAt, Instant now) {
        if (setAt == null) {
            return false;
        }
        int days = securityPolicyService.current().rotationDays();
        return Duration.between(setAt, now).compareTo(Duration.ofDays(days)) > 0;
    }

    public int rotationDays() {
        return securityPolicyService.current().rotationDays();
    }

    private static Set<String> loadDenylist() {
        Set<String> entries = new HashSet<>();
        try (InputStream in = PasswordPolicyService.class.getClassLoader().getResourceAsStream(DENYLIST_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + DENYLIST_RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String value = line.trim().toLowerCase(Locale.ROOT);
                    if (!value.isEmpty() && !value.startsWith("#")) {
                        entries.add(value);
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read " + DENYLIST_RESOURCE, ex);
        }
        return Set.copyOf(entries);
    }
}
