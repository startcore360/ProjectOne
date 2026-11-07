package com.edu360.api.config.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
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
 * Exercises the real HTTP calls KeycloakAdminClient makes against a mocked Keycloak server -
 * now authenticating once as the master-realm admin (see KeycloakProperties' class comment for
 * why), then calling the TARGET realm's admin endpoints, so both URLs stay covered without
 * needing a live Keycloak instance.
 */
class KeycloakAdminClientTest {

    private static final String REALM = "abc-university";

    private KeycloakProperties properties() {
        KeycloakProperties properties = new KeycloakProperties();
        properties.setBaseUrl("http://kc.test");
        properties.setMasterAdminUsername("kcadmin");
        properties.setMasterAdminPassword("kcadmin");
        return properties;
    }

    @Test
    void provisionRealm_keepsTheForcedPasswordChangeWorkingInNewRealms() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakProperties properties = properties();
        properties.setFrontendClientId("edu360-frontend");
        KeycloakAdminClient client = new KeycloakAdminClient(builder, properties);
        String profileUrl = "http://kc.test/admin/realms/" + REALM + "/users/profile";

        server.expect(requestTo("http://kc.test/realms/master/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://kc.test/admin/realms"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED));
        server.expect(requestTo(profileUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"attributes\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(profileUrl))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.unmanagedAttributePolicy").value("ENABLED"))
                .andExpect(jsonPath("$.attributes").isArray())
                .andRespond(withSuccess());
        server.expect(requestTo("http://kc.test/admin/realms/" + REALM + "/clients"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.protocolMappers[0].config['claim.name']").value("must_change_password"))
                .andExpect(jsonPath("$.protocolMappers[0].config['access.token.claim']").value("true"))
                .andRespond(withStatus(HttpStatus.CREATED));

        client.provisionRealm(REALM, "ABC University");

        server.verify();
    }

    @Test
    void provisionUser_createsAccountWithMustChangePasswordAttribute() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient client = new KeycloakAdminClient(builder, properties());

        server.expect(requestTo("http://kc.test/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://kc.test/admin/realms/" + REALM + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(URI.create("http://kc.test/admin/realms/" + REALM + "/users/kc-user-123")));

        String keycloakId = client.provisionUser(REALM, "Jane Teacher", "jane@edu360.local", "Temp#Pass1", true);

        assertThat(keycloakId).isEqualTo("kc-user-123");
        server.verify();
    }

    @Test
    void provisionUser_missingLocationHeader_throwsProvisioningException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient client = new KeycloakAdminClient(builder, properties());

        server.expect(requestTo("http://kc.test/realms/master/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://kc.test/admin/realms/" + REALM + "/users"))
                .andRespond(withStatus(HttpStatus.CREATED));

        org.junit.jupiter.api.Assertions.assertThrows(KeycloakProvisioningException.class,
                () -> client.provisionUser(REALM, "Jane", "jane@edu360.local", "Temp#Pass1", true));
    }

    @Test
    void resetPassword_updatesCredentialAndClearsMustChangePasswordAttribute() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient client = new KeycloakAdminClient(builder, properties());

        server.expect(requestTo("http://kc.test/realms/master/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://kc.test/admin/realms/" + REALM + "/users/kc-user-123/reset-password"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
        // setMustChangePasswordAttribute now GETs the current representation first and merges
        // in just the one changed field, instead of PUTting a bare {"attributes": ...} body -
        // found by actually calling it against a real Keycloak and watching it silently wipe a
        // user's firstName/lastName/email (see that method's own javadoc).
        server.expect(requestTo("http://kc.test/admin/realms/" + REALM + "/users/kc-user-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"kc-user-123\",\"username\":\"jane\",\"firstName\":\"Jane\",\"lastName\":\"Teacher\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://kc.test/admin/realms/" + REALM + "/users/kc-user-123"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        client.resetPassword(REALM, "kc-user-123", "NewPass#1");
        server.verify();
    }

    @Test
    void clearBruteForceLockout_sendsDeleteToAttackDetectionEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient client = new KeycloakAdminClient(builder, properties());

        server.expect(requestTo("http://kc.test/realms/master/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "http://kc.test/admin/realms/" + REALM + "/attack-detection/brute-force/users/kc-user-123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.clearBruteForceLockout(REALM, "kc-user-123");

        server.verify();
    }
}
