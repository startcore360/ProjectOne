package com.edu360.api.config.keycloak;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

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

    /** Keycloak's user attribute holding "must change password at next login" (see the Sprint 1
     *  identity migration brief: app.user_account has no such column in the approved V4.0
     *  schema, so this state lives entirely in Keycloak as a custom attribute, read back out as
     *  a JWT claim via each realm's protocol mapper - see realm-export-*.json). */
    static final String MUST_CHANGE_PASSWORD_ATTRIBUTE = "must_change_password";

    private final RestClient restClient;
    private final KeycloakProperties properties;

    public KeycloakAdminClient(RestClient.Builder restClientBuilder, KeycloakProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    /** Creates the Keycloak account in the given realm and returns Keycloak's user id (OD-10).
     *  mustChangePassword controls the initial must_change_password attribute. */
    public String provisionUser(String realm, String name, String email, String initialPassword,
                                 boolean mustChangePassword) {
        String adminToken = fetchMasterAdminAccessToken();
        return createUser(adminToken, realm, name, email, initialPassword, mustChangePassword);
    }

    private String fetchMasterAdminAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        // "admin-cli" is Keycloak's own built-in public client in the master realm, present in
        // every Keycloak instance by default specifically for this kind of server-side admin
        // bootstrap - not a client this app defines itself.
        form.add("client_id", "admin-cli");
        form.add("username", properties.getMasterAdminUsername());
        form.add("password", properties.getMasterAdminPassword());

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

    private String createUser(String adminToken, String realm, String name, String email,
                               String initialPassword, boolean mustChangePassword) {
        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", initialPassword,
                "temporary", false);
        // Keycloak's default "Verify Profile" required action triggers automatically - even
        // though we never set it explicitly - whenever a managed profile field (lastName) is
        // missing, which would reintroduce the same "account not fully set up" problem the
        // non-temporary credential above was meant to fix. Split the single "name" field this
        // app collects into first/last so the profile is considered complete. emailVerified is
        // set true for the same reason: there's no email-verification flow in this app, and an
        // account the admin just created is treated as already trusted.
        String[] nameParts = name.trim().split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : nameParts[0];
        Map<String, Object> userRepresentation = Map.of(
                "username", email,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(credential),
                "attributes", Map.of(MUST_CHANGE_PASSWORD_ATTRIBUTE, List.of(String.valueOf(mustChangePassword))));

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
            // A prior attempt (e.g. a retried dev seeding) can leave the Keycloak account
            // behind. Rather than fail the whole provisioning step, reuse that existing account.
            if (ex.getStatusCode() == HttpStatusCode.valueOf(409)) {
                return findUserIdByEmail(adminToken, realm, email);
            }
            throw ex;
        }
    }

    private String findUserIdByEmail(String adminToken, String realm, String email) {
        String searchUrl = properties.getBaseUrl() + "/admin/realms/" + realm
                + "/users?username=" + email + "&exact=true";
        List<Map<String, Object>> matches = restClient.get()
                .uri(searchUrl)
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
        String adminToken = fetchMasterAdminAccessToken();
        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", newPassword,
                "temporary", false);

        restClient.put()
                .uri(properties.getBaseUrl() + "/admin/realms/" + realm
                        + "/users/" + keycloakUserId + "/reset-password")
                .header("Authorization", "Bearer " + adminToken)
                .body(credential)
                .retrieve()
                .toBodilessEntity();

        setMustChangePasswordAttribute(adminToken, realm, keycloakUserId, false);
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
