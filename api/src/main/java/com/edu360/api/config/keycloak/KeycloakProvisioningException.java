package com.edu360.api.config.keycloak;

/** Thrown when Keycloak's Admin API doesn't behave as expected during account provisioning. */
public class KeycloakProvisioningException extends RuntimeException {
    public KeycloakProvisioningException(String message) {
        super(message);
    }

    public KeycloakProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
