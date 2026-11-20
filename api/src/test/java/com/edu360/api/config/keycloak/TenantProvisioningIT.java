package com.edu360.api.config.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Exercises dynamic realm, frontend-client and user provisioning against the running Keycloak service. */
class TenantProvisioningIT {

    @Test
    void createsTenantRealmAndUserThenCompensatesCleanly() {
        String baseUrl = System.getenv().getOrDefault("KEYCLOAK_BASE_URL", "http://127.0.0.1:8081");
        KeycloakProperties properties = new KeycloakProperties();
        properties.setBaseUrl(baseUrl);
        properties.setMasterAdminUsername(System.getenv().getOrDefault("KEYCLOAK_MASTER_ADMIN_USERNAME", "kcadmin"));
        properties.setMasterAdminPassword(System.getenv().getOrDefault("KEYCLOAK_MASTER_ADMIN_PASSWORD", "kcadmin"));
        properties.setFrontendClientId("edu360-frontend");
        properties.setFrontendRedirectUris(List.of("http://localhost:5173/*"));
        KeycloakAdminClient client = new KeycloakAdminClient(RestClient.builder(), properties);
        String realm = "edu360-a8-it-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        try {
            client.provisionRealm(realm, "EDU360 A8 Provisioning IT");
            String userId = client.provisionUser(realm, "A8 Integration User",
                    "a8-it-" + UUID.randomUUID() + "@example.test", "Temporary#Pass1", true);
            assertThat(userId).isNotBlank();
            assertThat(client.getUser(realm, userId)).isPresent();
            client.deleteUser(realm, userId);
            assertThat(client.getUser(realm, userId)).isEmpty();
        } finally {
            client.deleteRealm(realm);
        }
    }
}
