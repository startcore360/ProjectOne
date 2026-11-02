package com.edu360.api.config.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Exercises the real HTTP calls KeycloakAdminClient makes against a mocked Keycloak server,
 * so the URL-building (where a full "http://host" value is embedded in the path rather than
 * passed as a URI template variable - getting it backwards previously broke every call with
 * "URI with undefined scheme") stays covered without needing a live Keycloak instance.
 */
class KeycloakAdminClientTest {

    private KeycloakProperties properties() {
        KeycloakProperties properties = new KeycloakProperties();
        properties.setBaseUrl("http://kc.test");
        properties.setRealm("edu360");
        properties.setAdminClientId("edu360-admin-cli");
        properties.setAdminClientSecret("secret");
        return properties;
    }

    @Test
    void provisionUser_createsAccountAndAssignsRealmRole() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient client = new KeycloakAdminClient(builder, properties());

        server.expect(requestTo("http://kc.test/realms/edu360/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://kc.test/admin/realms/edu360/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(URI.create("http://kc.test/admin/realms/edu360/users/kc-user-123")));

        server.expect(requestTo("http://kc.test/admin/realms/edu360/roles/TEACHER"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"TEACHER\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://kc.test/admin/realms/edu360/users/kc-user-123/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        String keycloakId = client.provisionUser("Jane Teacher", "jane@edu360.local", "Temp#Pass1", "TEACHER");

        assertThat(keycloakId).isEqualTo("kc-user-123");
        server.verify();
    }

    @Test
    void provisionUser_missingLocationHeader_throwsProvisioningException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient client = new KeycloakAdminClient(builder, properties());

        server.expect(requestTo("http://kc.test/realms/edu360/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://kc.test/admin/realms/edu360/users"))
                .andRespond(withStatus(HttpStatus.CREATED));

        org.junit.jupiter.api.Assertions.assertThrows(KeycloakProvisioningException.class,
                () -> client.provisionUser("Jane", "jane@edu360.local", "Temp#Pass1", "TEACHER"));
    }
}
