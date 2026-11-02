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
 * Talks to Keycloak's Admin REST API to provision accounts (OD-80: registration stays an
 * application operation, but the credential itself now lives in Keycloak, not this app).
 * Nothing here ever stores a password locally — it's forwarded once and then forgotten.
 * The credential is set as immediately usable, not "temporary" with a forced change: login
 * goes through the Direct Grant (ROPC) flow entirely inside this app's own UI, and Keycloak's
 * forced-password-change step only exists as an interactive, browser-hosted page that ROPC
 * has no way to show or complete - a temporary credential would make the account permanently
 * unable to log in via this flow.
 */
@Component
public class KeycloakAdminClient {

    private final RestClient restClient;
    private final KeycloakProperties properties;

    public KeycloakAdminClient(RestClient.Builder restClientBuilder, KeycloakProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    /** Creates the Keycloak account, assigns the realm role, and returns Keycloak's user id (OD-10). */
    public String provisionUser(String name, String email, String initialPassword, String roleName) {
        String adminToken = fetchAdminAccessToken();
        String keycloakUserId = createUser(adminToken, name, email, initialPassword);
        assignRealmRole(adminToken, keycloakUserId, roleName);
        return keycloakUserId;
    }

    private String fetchAdminAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getAdminClientId());
        form.add("client_secret", properties.getAdminClientSecret());

        String tokenUrl = properties.getBaseUrl() + "/realms/" + properties.getRealm()
                + "/protocol/openid-connect/token";
        Map<String, Object> response = restClient.post()
                .uri(tokenUrl)
                .body(form)
                .retrieve()
                .body(Map.class);

        Object token = response == null ? null : response.get("access_token");
        if (token == null) {
            throw new KeycloakProvisioningException("Keycloak did not return an admin access token");
        }
        return token.toString();
    }

    private String createUser(String adminToken, String name, String email, String initialPassword) {
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
                "credentials", List.of(credential));

        String usersUrl = properties.getBaseUrl() + "/admin/realms/" + properties.getRealm() + "/users";
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
            // A prior attempt (e.g. a retried dev seeding, or a register() call whose local save
            // then failed) can leave the Keycloak account behind without a matching local User
            // row. Rather than fail the whole provisioning step, reuse that existing account.
            if (ex.getStatusCode() == HttpStatusCode.valueOf(409)) {
                return findUserIdByEmail(adminToken, email);
            }
            throw ex;
        }
    }

    private String findUserIdByEmail(String adminToken, String email) {
        String searchUrl = properties.getBaseUrl() + "/admin/realms/" + properties.getRealm()
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
     *  current password has already been verified via KeycloakAuthClient.login. */
    public void resetPassword(String keycloakUserId, String newPassword) {
        String adminToken = fetchAdminAccessToken();
        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", newPassword,
                "temporary", false);

        restClient.put()
                .uri(properties.getBaseUrl() + "/admin/realms/" + properties.getRealm()
                        + "/users/" + keycloakUserId + "/reset-password")
                .header("Authorization", "Bearer " + adminToken)
                .body(credential)
                .retrieve()
                .toBodilessEntity();
    }

    private void assignRealmRole(String adminToken, String keycloakUserId, String roleName) {
        String realmPath = properties.getBaseUrl() + "/admin/realms/" + properties.getRealm();

        Map<String, Object> role = restClient.get()
                .uri(realmPath + "/roles/" + roleName)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .body(Map.class);
        if (role == null) {
            throw new KeycloakProvisioningException("Realm role not found in Keycloak: " + roleName);
        }

        restClient.post()
                .uri(realmPath + "/users/" + keycloakUserId + "/role-mappings/realm")
                .header("Authorization", "Bearer " + adminToken)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }
}
