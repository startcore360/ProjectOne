package com.edu360.api.config.keycloak;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.edu360.api.exception.DuplicateResourceException;

/**
 * WHAT: talks to Keycloak's Admin REST API to provision accounts and manage their credentials -
 * now parameterized by realm, since this app administers many realms (every tenant realm, plus
 * the platform realm), not one fixed realm.
 *
 * HOW admin access works: authenticates once as the master-realm admin account (see
 * KeycloakProperties' class comment for why), then calls /admin/realms/{realm}/... for whatever
 * realm the caller asks about. Nothing here ever stores a password locally - it is forwarded
 * once and then forgotten.
 */
@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    /** Keycloak's user attribute holding "must change password at next login" (see the Sprint 1
     *  identity migration brief: app.user_account has no such column in the approved V4.0
     *  schema, so this state lives entirely in Keycloak as a custom attribute, read back out as
     *  a JWT claim via each realm's protocol mapper - see realm-export-*.json). */
    public static final String MUST_CHANGE_PASSWORD_ATTRIBUTE = "must_change_password";

    private final RestClient restClient;
    private final KeycloakProperties properties;

    public KeycloakAdminClient(RestClient.Builder restClientBuilder, KeycloakProperties properties) {
        RestClient client;
        try {
            client = restClientBuilder.build();
        } catch (Throwable t) {
            client = restClientBuilder
                    .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
                    .build();
        }
        this.restClient = client;
        this.properties = properties;
    }

    /** Creates the Keycloak account in the given realm and returns Keycloak's user id (OD-10).
     *  mustChangePassword controls the initial must_change_password attribute. */
    public String provisionUser(String realm, String name, String email, String initialPassword,
                                 boolean mustChangePassword) {
        String adminToken = fetchMasterAdminAccessToken();
        return createUser(adminToken, realm, name, email, email, initialPassword, mustChangePassword);
    }

    /** Provisions a student account whose Keycloak username is the institution-generated student code. */
    public String provisionStudentUser(String realm, String name, String studentCode, String initialPassword,
                                       boolean mustChangePassword) {
        String adminToken = fetchMasterAdminAccessToken();
        return createUser(adminToken, realm, name, studentCode, null, initialPassword, mustChangePassword);
    }

    /** Removes a just-provisioned identity when its enclosing database transaction cannot complete. */
    public void deleteUser(String realm, String keycloakUserId) {
        String adminToken = fetchMasterAdminAccessToken();
        restClient.delete()
                .uri(properties.getBaseUrl() + "/admin/realms/" + realm + "/users/" + keycloakUserId)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Provisions a new Keycloak realm and frontend client for a tenant.
     * Idempotent: if the realm or client already exists, completes without error.
     */
    public void provisionRealm(String realm, String displayName) {
        String adminToken = fetchMasterAdminAccessToken();
        try {
            Map<String, Object> realmRep = Map.of(
                    "realm", realm,
                    "displayName", displayName,
                    "enabled", true);
            restClient.post()
                    .uri(properties.getBaseUrl() + "/admin/realms")
                    .header("Authorization", "Bearer " + adminToken)
                    .body(realmRep)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() != HttpStatusCode.valueOf(409)) {
                throw new KeycloakProvisioningException("Failed to provision realm '" + realm + "' in Keycloak: " + ex.getMessage(), ex);
            }
        }

        allowCustomUserAttributes(adminToken, realm);

        try {
            Map<String, Object> clientRep = Map.of(
                    "clientId", properties.getFrontendClientId(),
                    "enabled", true,
                    "publicClient", true,
                    "directAccessGrantsEnabled", true,
                    "protocolMappers", List.of(mustChangePasswordClaimMapper()),
                    // PRODUCTION (docker/PRODUCTION_TENANT_ROUTING.txt, P7): dev addresses. Must come from configuration.
                    "redirectUris", List.of("http://*.edu360.local:5173/*", "http://localhost:5173/*"));
            restClient.post()
                    .uri(properties.getBaseUrl() + "/admin/realms/" + realm + "/clients")
                    .header("Authorization", "Bearer " + adminToken)
                    .body(clientRep)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() != HttpStatusCode.valueOf(409)) {
                throw new KeycloakProvisioningException("Failed to register frontend client in realm '" + realm + "': " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * Keycloak 26 silently drops user attributes its profile schema doesn't declare, which would
     * lose must_change_password. The seeded realms get this from docker/keycloak/provision-realms.ps1,
     * so realms created at runtime have to set it here.
     */
    private void allowCustomUserAttributes(String adminToken, String realm) {
        String profileUrl = properties.getBaseUrl() + "/admin/realms/" + realm + "/users/profile";
        try {
            Map<String, Object> profile = new HashMap<>(restClient.get()
                    .uri(profileUrl)
                    .header("Authorization", "Bearer " + adminToken)
                    .retrieve()
                    .body(Map.class));
            profile.put("unmanagedAttributePolicy", "ENABLED");
            restClient.put()
                    .uri(profileUrl)
                    .header("Authorization", "Bearer " + adminToken)
                    .body(profile)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to configure the user profile of realm '" + realm + "': " + ex.getMessage(), ex);
        }
    }

    /** Puts must_change_password in the token, where MustChangePasswordFilter reads it. Matches the
     *  mapper in docker/keycloak/realm-export-*.json. */
    private static Map<String, Object> mustChangePasswordClaimMapper() {
        return Map.of(
                "name", "must-change-password",
                "protocol", "openid-connect",
                "protocolMapper", "oidc-usermodel-attribute-mapper",
                "consentRequired", false,
                "config", Map.of(
                        "user.attribute", MUST_CHANGE_PASSWORD_ATTRIBUTE,
                        "claim.name", MUST_CHANGE_PASSWORD_ATTRIBUTE,
                        "jsonType.label", "boolean",
                        "id.token.claim", "true",
                        "access.token.claim", "true",
                        "userinfo.token.claim", "true"));
    }

    private String fetchMasterAdminAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        // BS-23: Prefer confidential client credentials grant in production when configured
        if (properties.getAdminClientId() != null && !properties.getAdminClientId().isBlank()
                && properties.getAdminClientSecret() != null && !properties.getAdminClientSecret().isBlank()) {
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.getAdminClientId());
            form.add("client_secret", properties.getAdminClientSecret());
        } else {
            form.add("grant_type", "password");
            // "admin-cli" is Keycloak's own built-in public client in the master realm, present in
            // every Keycloak instance by default specifically for this kind of server-side admin
            // bootstrap - not a client this app defines itself.
            form.add("client_id", "admin-cli");
            form.add("username", properties.getMasterAdminUsername());
            form.add("password", properties.getMasterAdminPassword());
        }

        String tokenUrl = properties.getBaseUrl() + "/realms/master/protocol/openid-connect/token";
        Map<String, Object> response = restClient.post()
                .uri(tokenUrl)
                .body(form)
                .retrieve()
                .body(Map.class);

        Object token = response == null ? null : response.get("access_token");
        if (token == null) {
            throw new KeycloakProvisioningException("Keycloak did not return a master admin access token");
        }
        return token.toString();
    }

    private String createUser(String adminToken, String realm, String name, String username, String email,
                               String initialPassword, boolean mustChangePassword) {
        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", initialPassword,
                "temporary", false);
        // Split the supplied display name into both managed Keycloak name fields. Email is
        // intentionally omitted for students: their login identity is the student code.
        String[] nameParts = name.trim().split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : nameParts[0];
        Map<String, Object> userRepresentation = new HashMap<>();
        userRepresentation.put("username", username);
        userRepresentation.put("firstName", firstName);
        userRepresentation.put("lastName", lastName);
        userRepresentation.put("enabled", true);
        userRepresentation.put("credentials", List.of(credential));
        userRepresentation.put("attributes", Map.of(MUST_CHANGE_PASSWORD_ATTRIBUTE, List.of(String.valueOf(mustChangePassword))));
        if (email != null && !email.isBlank()) {
            userRepresentation.put("email", email);
            userRepresentation.put("emailVerified", true);
        }

        String usersUrl = properties.getBaseUrl() + "/admin/realms/" + realm + "/users";
        try {
            var response = restClient.post()
                    .uri(usersUrl)
                    .header("Authorization", "Bearer " + adminToken)
                    .body(userRepresentation)
                    .retrieve()
                    .toBodilessEntity();

            String location = response.getHeaders().getFirst("Location");
            if (location == null) {
                throw new KeycloakProvisioningException("Keycloak did not return the new user's location");
            }
            return location.substring(location.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatusCode.valueOf(409)) {
                throw new DuplicateResourceException("A user with username '" + username + "' already exists in identity provider (BS-22).");
            }
            throw ex;
        }
    }

    private String findUserIdByEmail(String adminToken, String realm, String email) {
        // BS-22: Encode query parameters safely using URI template expansion
        List<Map<String, Object>> matches = restClient.get()
                .uri(properties.getBaseUrl() + "/admin/realms/{realm}/users?username={username}&exact=true", realm, email)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .body(List.class);

        if (matches == null || matches.isEmpty()) {
            throw new KeycloakProvisioningException(
                    "Keycloak reported a conflict creating " + email + " but no matching user was found");
        }
        return matches.get(0).get("id").toString();
    }

    /** Overwrites the user's Keycloak credential - used by AuthService.changePassword after the
     *  current password has already been verified via KeycloakAuthClient.login. Also clears the
     *  must_change_password attribute, since a change just happened. */
    public void resetPassword(String realm, String keycloakUserId, String newPassword) {
        resetPassword(realm, keycloakUserId, newPassword, false);
    }

    /**
     * Resets a user's password with configurable must_change_password policy.
     */
    public void resetPassword(String realm, String keycloakUserId, String newPassword, boolean mustChangePassword) {
        String adminToken = fetchMasterAdminAccessToken();
        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", newPassword,
                "temporary", mustChangePassword);

        restClient.put()
                .uri(properties.getBaseUrl() + "/admin/realms/" + realm
                        + "/users/" + keycloakUserId + "/reset-password")
                .header("Authorization", "Bearer " + adminToken)
                .body(credential)
                .retrieve()
                .toBodilessEntity();

        setMustChangePasswordAttribute(adminToken, realm, keycloakUserId, mustChangePassword);
    }

    /**
     * AUTH_DEVICES_MANAGE (Sign-in epic, Scope: Self): revokes one Keycloak user session by its
     * "sid" claim, via Keycloak's own admin logout-session endpoint - DELETE /admin/realms/
     * {realm}/sessions/{sessionId}. This is the same admin-token pattern every other method in
     * this class uses (fetchMasterAdminAccessToken + a single admin REST call), reused here
     * rather than standing up a second HTTP client, per the Sign-in epic self-service brief.
     *
     * Reuses sessionsUrl for both calls rather than adding a second idempotent-DELETE helper:
     * Keycloak itself answers a repeat DELETE of an already-gone session as a no-op (204), so no
     * extra existence check is added here before calling it.
     */
    /** One live Keycloak user session. start/lastAccess are epoch milliseconds, as Keycloak
     *  reports them. ipAddress is whatever Keycloak saw - with this app's server-side password
     *  grant that is the API server, not the user's device, so callers prefer their own record. */
    public record UserSessionInfo(String id, String ipAddress, Long start, Long lastAccess) {
    }

    /**
     * AUTH_DEVICES_MANAGE / SES-01: every session Keycloak currently holds for this user
     * (GET /admin/realms/{realm}/users/{userId}/sessions). Keycloak is the authority on which
     * sessions are alive - expired or signed-out ones simply are not in this list.
     */
    @SuppressWarnings("unchecked")
    public List<UserSessionInfo> listUserSessions(String realm, String keycloakUserId) {
        String adminToken = fetchMasterAdminAccessToken();
        List<Map<String, Object>> body = restClient.get()
                .uri(properties.getBaseUrl() + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/sessions")
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .body(List.class);
        if (body == null) {
            return List.of();
        }
        return body.stream()
                .map(s -> new UserSessionInfo(
                        s.get("id") == null ? null : s.get("id").toString(),
                        s.get("ipAddress") == null ? null : s.get("ipAddress").toString(),
                        asLong(s.get("start")),
                        asLong(s.get("lastAccess"))))
                .filter(s -> s.id() != null)
                .toList();
    }

    private static Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void logoutSession(String realm, String sessionId) {
        String adminToken = fetchMasterAdminAccessToken();
        restClient.delete()
                .uri(properties.getBaseUrl() + "/admin/realms/" + realm + "/sessions/" + sessionId)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * ROLE_ASSIGN (WF-27, PQ-20): logs out all active sessions for a user so that changed
     * role permissions take effect immediately upon next login:
     * POST /admin/realms/{realm}/users/{userId}/logout
     */
    public void logoutUser(String realm, String keycloakUserId) {
        try {
            String adminToken = fetchMasterAdminAccessToken();
            restClient.post()
                    .uri(properties.getBaseUrl() + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/logout")
                    .header("Authorization", "Bearer " + adminToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("KeycloakAdminClient.logoutUser skipped or non-fatal for user {} in realm {}: {}",
                    keycloakUserId, realm, ex.getMessage());
        }
    }

    /**
     * Sets enabled flag for a Keycloak user account (e.g. disabled upon deactivation):
     * PUT /admin/realms/{realm}/users/{userId}
     */
    @SuppressWarnings("unchecked")
    public void setUserEnabled(String realm, String keycloakUserId, boolean enabled) {
        try {
            String adminToken = fetchMasterAdminAccessToken();
            String userUrl = properties.getBaseUrl() + "/admin/realms/" + realm + "/users/" + keycloakUserId;
            Map<String, Object> current = restClient.get()
                    .uri(userUrl)
                    .header("Authorization", "Bearer " + adminToken)
                    .retrieve()
                    .body(Map.class);
            if (current != null) {
                Map<String, Object> updated = new java.util.HashMap<>(current);
                updated.put("enabled", enabled);
                restClient.put()
                        .uri(userUrl)
                        .header("Authorization", "Bearer " + adminToken)
                        .body(updated)
                        .retrieve()
                        .toBodilessEntity();
            }
        } catch (Exception ex) {
            log.warn("KeycloakAdminClient.setUserEnabled({}) skipped or non-fatal for user {} in realm {}: {}",
                    enabled, keycloakUserId, realm, ex.getMessage());
        }
    }

    /**
     * USER_PASSWORD_RESET_UNLOCK's real "unlock" half: clears Keycloak's own brute-force/
     * attack-detection lockout record for one user - DELETE /admin/realms/{realm}/
     * attack-detection/brute-force/users/{userId}, the endpoint Keycloak's own docs name for
     * this exact purpose (distinct from resetPassword, which only gives the user a fresh
     * credential and does nothing to this separate failure-count record).
     *
     * Same admin-token + single REST call pattern as every other method in this class
     * (fetchMasterAdminAccessToken + one admin call); like logoutSession, a repeat DELETE of an
     * already-clear record is a no-op on Keycloak's side, so no existence check is added here
     * first.
     */
    // ------------------------------------------------------------------ B3: sign-in security

    /** I-01: user attribute the realm's direct-grant flow checks before asking for the code. */
    public static final String MFA_REQUIRED_ATTRIBUTE = "mfa_required";
    /** Keycloak's own required action that shows its authenticator set-up page. */
    public static final String CONFIGURE_TOTP_ACTION = "CONFIGURE_TOTP";

    /** The parts of a Keycloak user this app's sign-in decisions need. */
    public record KeycloakUser(String id, String username, boolean enabled, Map<String, List<String>> attributes,
                               List<String> requiredActions) {
        public boolean attributeTrue(String name) {
            List<String> values = attributes == null ? null : attributes.get(name);
            return values != null && values.stream().anyMatch(v -> "true".equalsIgnoreCase(v));
        }
    }

    /** One stored credential: type ("password", "otp", ...) and when it was created. */
    public record CredentialInfo(String type, java.time.Instant createdAt) {
    }

    // ------------------------------------------------- in-app authenticator set-up (MFA-ENROL)

    /** A started authenticator set-up: shown once to the user as a QR code + typed secret. */
    public record OtpEnrolment(String enrolmentId, String qrCode, String secret, int expiresInSeconds) {
    }

    public enum OtpConfirmOutcome { CONFIRMED, INVALID_CODE, TOO_MANY_ATTEMPTS, NOT_FOUND, ALREADY_ENROLLED }

    public record OtpConfirmResult(OtpConfirmOutcome outcome, int attemptsLeft) {
    }

    private static final String OTP_EXTENSION_PATH = "/edu360-otp";

    /**
     * Starts an authenticator set-up through the EDU 360 Keycloak extension
     * (keycloak/edu360-otp-extension): POST /realms/{realm}/edu360-otp/enrolment. The secret is
     * kept inside Keycloak for 10 minutes. Empty when the extension is not installed or the user
     * already has an authenticator - the caller then falls back to Keycloak's own set-up page.
     */
    @SuppressWarnings("unchecked")
    public java.util.Optional<OtpEnrolment> startOtpEnrolment(String realm, String username) {
        try {
            String adminToken = fetchMasterAdminAccessToken();
            Map<String, Object> body = restClient.post()
                    .uri(properties.getBaseUrl() + "/realms/" + realm + OTP_EXTENSION_PATH + "/enrolment")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("username", username))
                    .retrieve()
                    .body(Map.class);
            if (body == null || body.get("enrolmentId") == null) {
                return java.util.Optional.empty();
            }
            int expires = body.get("expiresInSeconds") instanceof Number n ? n.intValue() : 600;
            return java.util.Optional.of(new OtpEnrolment(body.get("enrolmentId").toString(),
                    String.valueOf(body.get("qrCode")), String.valueOf(body.get("secret")), expires));
        } catch (RuntimeException ex) {
            log.warn("In-app authenticator set-up unavailable for realm {}: {}", realm, ex.getMessage());
            return java.util.Optional.empty();
        }
    }

    /** Confirms a set-up with the user's first code: POST .../enrolment/{id}/confirm. */
    @SuppressWarnings("unchecked")
    public OtpConfirmResult confirmOtpEnrolment(String realm, String username, String enrolmentId, String code,
                                                String deviceName) {
        String adminToken = fetchMasterAdminAccessToken();
        Map<String, Object> request = new HashMap<>();
        request.put("username", username);
        request.put("code", code);
        if (deviceName != null && !deviceName.isBlank()) {
            request.put("deviceName", deviceName);
        }
        try {
            restClient.post()
                    .uri(properties.getBaseUrl() + "/realms/" + realm + OTP_EXTENSION_PATH + "/enrolment/"
                            + java.net.URLEncoder.encode(enrolmentId, java.nio.charset.StandardCharsets.UTF_8) + "/confirm")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            return new OtpConfirmResult(OtpConfirmOutcome.CONFIRMED, 0);
        } catch (HttpClientErrorException ex) {
            Map<String, Object> body;
            try {
                body = ex.getResponseBodyAs(Map.class);
            } catch (RuntimeException parse) {
                body = Map.of();
            }
            String error = body == null || body.get("error") == null ? "" : body.get("error").toString();
            int left = body != null && body.get("attemptsLeft") instanceof Number n ? n.intValue() : 0;
            return switch (error) {
                case "invalid_code" -> new OtpConfirmResult(OtpConfirmOutcome.INVALID_CODE, left);
                case "too_many_attempts" -> new OtpConfirmResult(OtpConfirmOutcome.TOO_MANY_ATTEMPTS, 0);
                case "already_enrolled" -> new OtpConfirmResult(OtpConfirmOutcome.ALREADY_ENROLLED, 0);
                default -> new OtpConfirmResult(OtpConfirmOutcome.NOT_FOUND, 0);
            };
        }
    }

    /**
     * Finds an account by what the user typed: Keycloak username first (staff: email; college
     * student: student ID), then exact email. Empty when no such account exists.
     */
    @SuppressWarnings("unchecked")
    public java.util.Optional<KeycloakUser> findUser(String realm, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return java.util.Optional.empty();
        }
        String adminToken = fetchMasterAdminAccessToken();
        List<Map<String, Object>> matches = restClient.get()
                .uri(properties.getBaseUrl() + "/admin/realms/{realm}/users?username={u}&exact=true&briefRepresentation=false",
                        realm, identifier.trim())
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .body(List.class);
        if (matches == null || matches.isEmpty()) {
            matches = restClient.get()
                    .uri(properties.getBaseUrl() + "/admin/realms/{realm}/users?email={e}&exact=true&briefRepresentation=false",
                            realm, identifier.trim())
                    .header("Authorization", "Bearer " + adminToken)
                    .retrieve()
                    .body(List.class);
        }
        if (matches == null || matches.size() != 1) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(toKeycloakUser(matches.get(0)));
    }

    @SuppressWarnings("unchecked")
    public java.util.Optional<KeycloakUser> getUser(String realm, String keycloakUserId) {
        String adminToken = fetchMasterAdminAccessToken();
        try {
            Map<String, Object> rep = restClient.get()
                    .uri(properties.getBaseUrl() + "/admin/realms/" + realm + "/users/" + keycloakUserId)
                    .header("Authorization", "Bearer " + adminToken)
                    .retrieve()
                    .body(Map.class);
            return rep == null ? java.util.Optional.empty() : java.util.Optional.of(toKeycloakUser(rep));
        } catch (HttpClientErrorException.NotFound ex) {
            return java.util.Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static KeycloakUser toKeycloakUser(Map<String, Object> rep) {
        Map<String, List<String>> attributes = new HashMap<>();
        if (rep.get("attributes") instanceof Map<?, ?> raw) {
            raw.forEach((k, v) -> {
                if (v instanceof List<?> list) {
                    attributes.put(String.valueOf(k), list.stream().map(String::valueOf).toList());
                }
            });
        }
        List<String> required = rep.get("requiredActions") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        return new KeycloakUser(String.valueOf(rep.get("id")), String.valueOf(rep.get("username")),
                !Boolean.FALSE.equals(rep.get("enabled")), attributes, required);
    }

    /** Every credential Keycloak holds for the user (metadata only - never secret values). */
    @SuppressWarnings("unchecked")
    public List<CredentialInfo> listCredentials(String realm, String keycloakUserId) {
        String adminToken = fetchMasterAdminAccessToken();
        List<Map<String, Object>> body = restClient.get()
                .uri(properties.getBaseUrl() + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/credentials")
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .body(List.class);
        if (body == null) {
            return List.of();
        }
        return body.stream()
                .map(c -> new CredentialInfo(String.valueOf(c.get("type")),
                        c.get("createdDate") instanceof Number n ? java.time.Instant.ofEpochMilli(n.longValue()) : null))
                .toList();
    }

    /**
     * Sets (value non-null) or removes (value null) one user attribute, keeping every other field
     * - same GET-then-PUT reason as setMustChangePasswordAttribute.
     */
    @SuppressWarnings("unchecked")
    public void setUserAttribute(String realm, String keycloakUserId, String name, String value) {
        String adminToken = fetchMasterAdminAccessToken();
        String userUrl = properties.getBaseUrl() + "/admin/realms/" + realm + "/users/" + keycloakUserId;
        Map<String, Object> current = restClient.get().uri(userUrl)
                .header("Authorization", "Bearer " + adminToken).retrieve().body(Map.class);
        if (current == null) {
            throw new KeycloakProvisioningException("Could not read user " + keycloakUserId + " before updating it");
        }
        Map<String, Object> attributes = new HashMap<>(
                current.get("attributes") instanceof Map<?, ?> existing ? (Map<String, Object>) existing : Map.of());
        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, List.of(value));
        }
        Map<String, Object> updated = new HashMap<>(current);
        updated.put("attributes", attributes);
        restClient.put().uri(userUrl).header("Authorization", "Bearer " + adminToken)
                .body(updated).retrieve().toBodilessEntity();
    }

    /** Adds (add=true) or removes one Keycloak required action on the user. */
    @SuppressWarnings("unchecked")
    public void setRequiredAction(String realm, String keycloakUserId, String action, boolean add) {
        String adminToken = fetchMasterAdminAccessToken();
        String userUrl = properties.getBaseUrl() + "/admin/realms/" + realm + "/users/" + keycloakUserId;
        Map<String, Object> current = restClient.get().uri(userUrl)
                .header("Authorization", "Bearer " + adminToken).retrieve().body(Map.class);
        if (current == null) {
            throw new KeycloakProvisioningException("Could not read user " + keycloakUserId + " before updating it");
        }
        java.util.LinkedHashSet<String> actions = new java.util.LinkedHashSet<>(
                current.get("requiredActions") instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of());
        boolean changed = add ? actions.add(action) : actions.remove(action);
        if (!changed) {
            return;
        }
        Map<String, Object> updated = new HashMap<>(current);
        updated.put("requiredActions", List.copyOf(actions));
        restClient.put().uri(userUrl).header("Authorization", "Bearer " + adminToken)
                .body(updated).retrieve().toBodilessEntity();
    }

    /**
     * I-03: true while Keycloak's brute-force protection holds this account locked
     * (GET /attack-detection/brute-force/users/{id} -> "disabled"). Keycloak is the only store
     * of failed attempts, so the answer is the same on every API node and after a restart.
     */
    @SuppressWarnings("unchecked")
    public boolean isBruteForceLocked(String realm, String keycloakUserId) {
        String adminToken = fetchMasterAdminAccessToken();
        Map<String, Object> status = restClient.get()
                .uri(properties.getBaseUrl() + "/admin/realms/" + realm + "/attack-detection/brute-force/users/" + keycloakUserId)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .body(Map.class);
        return status != null && Boolean.TRUE.equals(status.get("disabled"));
    }

    // Generic admin calls for KeycloakRealmSecurityService (same package): one master-admin
    // token per call, same pattern as every method above.

    @SuppressWarnings("unchecked")
    <T> T adminGet(String path, Class<T> type) {
        String adminToken = fetchMasterAdminAccessToken();
        return restClient.get().uri(properties.getBaseUrl() + "/admin/realms/" + path)
                .header("Authorization", "Bearer " + adminToken).retrieve().body(type);
    }

    void adminPut(String path, Object body) {
        String adminToken = fetchMasterAdminAccessToken();
        restClient.put().uri(properties.getBaseUrl() + "/admin/realms/" + path)
                .header("Authorization", "Bearer " + adminToken).body(body).retrieve().toBodilessEntity();
    }

    /** POST; returns the Location header's last path segment (the new object's id), if any. */
    String adminPost(String path, Object body) {
        String adminToken = fetchMasterAdminAccessToken();
        var response = restClient.post().uri(properties.getBaseUrl() + "/admin/realms/" + path)
                .header("Authorization", "Bearer " + adminToken).body(body).retrieve().toBodilessEntity();
        String location = response.getHeaders().getFirst("Location");
        return location == null ? null : location.substring(location.lastIndexOf('/') + 1);
    }

    void adminDelete(String path) {
        String adminToken = fetchMasterAdminAccessToken();
        restClient.delete().uri(properties.getBaseUrl() + "/admin/realms/" + path)
                .header("Authorization", "Bearer " + adminToken).retrieve().toBodilessEntity();
    }

    public void clearBruteForceLockout(String realm, String keycloakUserId) {
        String adminToken = fetchMasterAdminAccessToken();
        restClient.delete()
                .uri(properties.getBaseUrl() + "/admin/realms/" + realm
                        + "/attack-detection/brute-force/users/" + keycloakUserId)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * WHAT: flips the must_change_password attribute without touching anything else about the
     * user.
     *
     * WHY this GETs the full representation first instead of just PUTting {"attributes": {...}}
     * directly (what this method originally did): found by actually calling it against a real
     * Keycloak and then trying to log that user back in - Keycloak's PUT /users/{id} treats a
     * partial body as the FULL new representation for the fields it manages, so a bare
     * {"attributes": ...} body silently wiped the user's firstName/lastName/email, which then
     * made every subsequent login attempt fail outright. Fetching the current representation and
     * only changing the one field keeps everything else exactly as it was.
     */
    @SuppressWarnings("unchecked")
    private void setMustChangePasswordAttribute(String adminToken, String realm, String keycloakUserId,
                                                 boolean mustChangePassword) {
        String userUrl = properties.getBaseUrl() + "/admin/realms/" + realm + "/users/" + keycloakUserId;
        Map<String, Object> current = restClient.get()
                .uri(userUrl)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .body(Map.class);
        if (current == null) {
            throw new KeycloakProvisioningException("Could not read user " + keycloakUserId + " before updating it");
        }

        java.util.Map<String, Object> attributes = new java.util.HashMap<>(
                current.get("attributes") instanceof Map<?, ?> existing
                        ? (Map<String, Object>) existing
                        : Map.of());
        attributes.put(MUST_CHANGE_PASSWORD_ATTRIBUTE, List.of(String.valueOf(mustChangePassword)));

        java.util.Map<String, Object> updated = new java.util.HashMap<>(current);
        updated.put("attributes", attributes);

        restClient.put()
                .uri(userUrl)
                .header("Authorization", "Bearer " + adminToken)
                .body(updated)
                .retrieve()
                .toBodilessEntity();
    }
}
