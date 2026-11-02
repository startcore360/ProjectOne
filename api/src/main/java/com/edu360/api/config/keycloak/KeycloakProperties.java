package com.edu360.api.config.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Connection details for Keycloak's Admin REST API, used only to provision new accounts
 * (AuthService.register / DevAdminSeeder). Separate from the resource-server JWT validation
 * config (spring.security.oauth2.resourceserver.jwt.*), which only needs the issuer/JWKS.
 */
@Component
@ConfigurationProperties(prefix = "app.keycloak")
public class KeycloakProperties {

    /** Keycloak server root, e.g. http://localhost:8081 (no trailing slash). */
    private String baseUrl;

    /** Realm this application's users live in. */
    private String realm;

    /** Confidential client with a service account that holds realm-management roles
     *  (manage-users, view-users, query-users). Used only server-side, never exposed. */
    private String adminClientId;
    private String adminClientSecret;

    /** The public client end users authenticate as (Direct Grant). No secret - this is the
     *  same client ID the frontend used to be configured with before login moved server-side. */
    private String frontendClientId;

    public String getFrontendClientId() {
        return frontendClientId;
    }

    public void setFrontendClientId(String frontendClientId) {
        this.frontendClientId = frontendClientId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getAdminClientId() {
        return adminClientId;
    }

    public void setAdminClientId(String adminClientId) {
        this.adminClientId = adminClientId;
    }

    public String getAdminClientSecret() {
        return adminClientSecret;
    }

    public void setAdminClientSecret(String adminClientSecret) {
        this.adminClientSecret = adminClientSecret;
    }
}
