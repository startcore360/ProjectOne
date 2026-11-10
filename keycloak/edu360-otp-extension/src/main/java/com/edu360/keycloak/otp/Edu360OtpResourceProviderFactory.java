package com.edu360.keycloak.otp;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/** Registers the "edu360-otp" realm resource: /realms/{realm}/edu360-otp/... */
public class Edu360OtpResourceProviderFactory implements RealmResourceProviderFactory {

    public static final String ID = "edu360-otp";

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new Edu360OtpResourceProvider(session);
    }

    @Override
    public void init(Config.Scope config) {
        // no configuration
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // nothing to do
    }

    @Override
    public void close() {
        // nothing to release
    }

    @Override
    public String getId() {
        return ID;
    }
}
