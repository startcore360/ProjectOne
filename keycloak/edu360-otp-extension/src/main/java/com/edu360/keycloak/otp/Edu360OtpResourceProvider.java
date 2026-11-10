package com.edu360.keycloak.otp;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

/** Serves /realms/{realm}/edu360-otp (see Edu360OtpResource). */
public class Edu360OtpResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public Edu360OtpResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new Edu360OtpResource(session);
    }

    @Override
    public void close() {
        // nothing to release
    }
}
