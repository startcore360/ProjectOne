package com.edu360.api.config.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WHAT: connection details for Keycloak, shared by every realm this app ever talks to - never
 * one realm's worth of config, since this app is now multi-tenant and multi-realm.
 *
 * WHY admin access is done through the MASTER realm instead of each realm's own admin-cli
 * client (the pre-migration design): each tenant realm having its own separately-secreted
 * admin-cli client does not scale to tenants being created dynamically at runtime (the real
 * V4.0 provisioning flow, not yet built - TENANT_CREATE). One set of master-realm credentials
 * with cross-realm admin rights (the same kind of account docker/keycloak/docker-compose.yml's
 * KC_BOOTSTRAP_ADMIN_USERNAME/PASSWORD already is) can administer ANY realm by name, so no new
 * secret is needed every time a tenant realm is created. In production this must be a real,
 * tightly-scoped platform-admin Keycloak account, supplied by the deployment environment -
 * never the dev bootstrap kcadmin/kcadmin shown in docker-compose.yml.
 */
@Component
@ConfigurationProperties(prefix = "app.keycloak")
public class KeycloakProperties {

    /** Keycloak server root, e.g. http://localhost:8081 (no trailing slash). */
    private String baseUrl;

    /** The one realm Platform Admin and the other five platform roles ever log in through -
     *  deliberately separate from every tenant realm (platform.platform_user has no tenant_id
     *  at all in the schema - never an implicit tenant RLS bypass). */
    private String platformRealm;

    /** Master-realm account with cross-realm admin rights, used for all account provisioning/
     *  password-reset/attribute operations against ANY realm (tenant or platform) by name. */
    private String masterAdminUsername;
    private String masterAdminPassword;

    /** Confidential client credentials for admin access (BS-23, preferred in production). */
    private String adminClientId;
    private String adminClientSecret;

    /** Confidential client credentials for identity events webhook (M-03 / PD-2). */
    private String identityEventsClientId = "edu360-identity-events";
    private String identityEventsClientSecret;

    /** The public client end users authenticate as (Direct Grant/ROPC). Same client ID by
     *  convention in every realm (see the three realm-export-*.json files) - no secret, since
     *  it is a public client. */
    private String frontendClientId;

    /** Local-dev-only hostname suffix stripped from the Host header to find a tenant's
     *  subdomain (see LoginTenantResolver) - e.g. ".edu360.local". Blank in production, where
     *  real subdomain/custom-domain routing is not built yet: see
     *  docker/PRODUCTION_TENANT_ROUTING.txt, P4. */
    private String localDevHostSuffix = "";

    /** The one hostname label that means "this is a platform login, not a tenant login" (e.g.
     *  "platform" for platform.edu360.local) - see LoginTenantResolver. */
    private String platformHostLabel = "platform";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPlatformRealm() {
        return platformRealm;
    }

    public void setPlatformRealm(String platformRealm) {
        this.platformRealm = platformRealm;
    }

    public String getMasterAdminUsername() {
        return masterAdminUsername;
    }

    public void setMasterAdminUsername(String masterAdminUsername) {
        this.masterAdminUsername = masterAdminUsername;
    }

    public String getMasterAdminPassword() {
        return masterAdminPassword;
    }

    public void setMasterAdminPassword(String masterAdminPassword) {
        this.masterAdminPassword = masterAdminPassword;
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

    public String getFrontendClientId() {
        return frontendClientId;
    }

    public void setFrontendClientId(String frontendClientId) {
        this.frontendClientId = frontendClientId;
    }

    public String getLocalDevHostSuffix() {
        return localDevHostSuffix;
    }

    public void setLocalDevHostSuffix(String localDevHostSuffix) {
        this.localDevHostSuffix = localDevHostSuffix;
    }

    public String getPlatformHostLabel() {
        return platformHostLabel;
    }

    public void setPlatformHostLabel(String platformHostLabel) {
        this.platformHostLabel = platformHostLabel;
    }

    /**
     * I-01: Keycloak's address as the user's BROWSER reaches it (the authenticator set-up page
     * is a Keycloak page). Differs from baseUrl wherever the API reaches Keycloak on an internal
     * address. No fallback: when unset, the enrolment hand-off says it is not available.
     */
    private String publicBaseUrl;

    /**
     * I-06: name of the common-password file Keycloak reads from its data/password-blacklists
     * folder (docker/keycloak/docker-compose.yml mounts api/src/main/resources/security/
     * common-passwords.txt there). Empty = Keycloak's own denylist step is not configured; the
     * API still refuses those passwords itself (PasswordPolicyService).
     */
    private String passwordDenylistName;

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getPasswordDenylistName() {
        return passwordDenylistName;
    }

    public void setPasswordDenylistName(String passwordDenylistName) {
        this.passwordDenylistName = passwordDenylistName;
    }

    public String getIdentityEventsClientId() {
        return identityEventsClientId;
    }

    public void setIdentityEventsClientId(String identityEventsClientId) {
        this.identityEventsClientId = identityEventsClientId;
    }

    public String getIdentityEventsClientSecret() {
        return identityEventsClientSecret;
    }

    public void setIdentityEventsClientSecret(String identityEventsClientSecret) {
        this.identityEventsClientSecret = identityEventsClientSecret;
    }
}
