package com.edu360.api.auth;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import com.edu360.api.config.keycloak.KeycloakProperties;

/**
 * Validates Keycloak service-account tokens for internal identity-event webhooks (M-03 / C4 / D3 / PD-2).
 *
 * Enforces:
 *  - Authorization header must be a Bearer token
 *  - Valid cryptographic signature against Keycloak platform realm
 *  - Issuer must be the configured platform realm
 *  - azp must match the configured identity-events client ID (e.g. edu360-identity-events)
 *  - Role "identity-events:write" must be granted to the client/service account
 *  - Token must not be expired
 *  - Regular user tokens (even Product Owner) and tokens from other clients are rejected with 401
 */
@Component
public class IdentityEventTokenValidator {

    public static final String REQUIRED_ROLE = "identity-events:write";
    private static final Logger log = LoggerFactory.getLogger(IdentityEventTokenValidator.class);

    private final KeycloakProperties keycloakProperties;
    private final JwtDecoder customJwtDecoder;
    private volatile JwtDecoder platformJwtDecoder;

    @Autowired
    public IdentityEventTokenValidator(KeycloakProperties keycloakProperties) {
        this(keycloakProperties, null);
    }

    public IdentityEventTokenValidator(KeycloakProperties keycloakProperties, JwtDecoder customJwtDecoder) {
        this.keycloakProperties = keycloakProperties;
        this.customJwtDecoder = customJwtDecoder;
    }

    public boolean isValidServiceToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            return false;
        }

        try {
            Jwt jwt = getDecoder().decode(token);
            if (jwt == null) {
                return false;
            }

            // Expiry check
            Instant now = Instant.now();
            if (jwt.getExpiresAt() != null && jwt.getExpiresAt().isBefore(now)) {
                return false;
            }

            // Issuer check (must be platform realm)
            String expectedIssuer = keycloakProperties.getBaseUrl() + "/realms/" + keycloakProperties.getPlatformRealm();
            if (jwt.getIssuer() == null || !expectedIssuer.equals(jwt.getIssuer().toString())) {
                return false;
            }

            // azp check (must be the dedicated service client)
            String expectedClientId = keycloakProperties.getIdentityEventsClientId();
            if (expectedClientId == null || expectedClientId.isBlank()) {
                return false;
            }
            String azp = jwt.getClaimAsString("azp");
            if (azp == null || !expectedClientId.equals(azp)) {
                return false;
            }

            // Role check (must have REQUIRED_ROLE)
            if (!hasRequiredRole(jwt, expectedClientId)) {
                return false;
            }

            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Service token validation failed: {}", ex.getMessage());
            return false;
        }
    }

    private boolean hasRequiredRole(Jwt jwt, String clientId) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
            if (roles.contains(REQUIRED_ROLE)) {
                return true;
            }
        }

        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null && resourceAccess.get(clientId) instanceof Map<?, ?> clientAccess) {
            if (clientAccess.get("roles") instanceof List<?> roles && roles.contains(REQUIRED_ROLE)) {
                return true;
            }
        }

        return false;
    }

    private JwtDecoder getDecoder() {
        if (customJwtDecoder != null) {
            return customJwtDecoder;
        }
        if (platformJwtDecoder == null) {
            synchronized (this) {
                if (platformJwtDecoder == null) {
                    String platformIssuer = keycloakProperties.getBaseUrl() + "/realms/" + keycloakProperties.getPlatformRealm();
                    platformJwtDecoder = JwtDecoders.fromIssuerLocation(platformIssuer);
                }
            }
        }
        return platformJwtDecoder;
    }
}
