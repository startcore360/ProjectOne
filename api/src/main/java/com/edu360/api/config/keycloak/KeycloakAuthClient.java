package com.edu360.api.config.keycloak;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.edu360.api.exception.InvalidCredentialsException;

/**
 * Forwards the actual login (and refresh) to Keycloak on the frontend's behalf, for whichever
 * realm the caller resolved (see LoginTenantResolver - the realm comes from the Host header,
 * never a client-supplied parameter, before this class is ever called): Frontend -> our
 * AuthController -> this class -> that realm's Keycloak token endpoint -> back through us ->
 * Frontend. The password passes through this server for one request and is never stored - only
 * the resulting Keycloak tokens are handed back. Uses the Direct (Resource Owner Password
 * Credentials) Grant, which trades Keycloak's redirect-based SSO page for keeping the login form
 * inside this app's own UI.
 */
@Component
public class KeycloakAuthClient {

    private final RestClient restClient;
    private final KeycloakProperties properties;
    // A private instance rather than an injected bean: Spring Boot 4's auto-configured Jackson
    // bean is tools.jackson.databind's JsonMapper, not this com.fasterxml.jackson ObjectMapper
    // - only used here to read one claim out of an already-trusted token, so no need to share
    // the app-wide Jackson configuration.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KeycloakAuthClient(RestClient.Builder restClientBuilder, KeycloakProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public record TokenResult(String accessToken, String refreshToken, long expiresInSeconds, String keycloakId,
                               boolean mustChangePassword) {
    }

    public TokenResult login(String realm, String identifier, String password) {
        return requestToken(realm, form -> {
            form.add("grant_type", "password");
            form.add("username", identifier);
            form.add("password", password);
        });
    }

    public TokenResult refresh(String realm, String refreshToken) {
        return requestToken(realm, form -> {
            form.add("grant_type", "refresh_token");
            form.add("refresh_token", refreshToken);
        });
    }

    private TokenResult requestToken(String realm, java.util.function.Consumer<MultiValueMap<String, String>> fill) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getFrontendClientId());
        fill.accept(form);

        String tokenUrl = properties.getBaseUrl() + "/realms/" + realm + "/protocol/openid-connect/token";
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(tokenUrl)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.Unauthorized ex) {
            // Keycloak's standard shape for a wrong password / expired or revoked refresh token.
            throw new InvalidCredentialsException("Invalid credentials");
        }

        if (response == null || response.get("access_token") == null) {
            throw new KeycloakProvisioningException("Keycloak did not return an access token");
        }

        String accessToken = response.get("access_token").toString();
        String refreshTokenValue = response.get("refresh_token") == null ? null
                : response.get("refresh_token").toString();
        long expiresIn = response.get("expires_in") == null ? 0
                : Long.parseLong(response.get("expires_in").toString());

        Map<String, Object> claims = claimsOf(accessToken);
        String subject = (String) claims.get("sub");
        boolean mustChangePassword = Boolean.parseBoolean(
                String.valueOf(claims.getOrDefault(KeycloakAdminClient.MUST_CHANGE_PASSWORD_ATTRIBUTE, "false")));

        return new TokenResult(accessToken, refreshTokenValue, expiresIn, subject, mustChangePassword);
    }

    /** Reads claims straight out of the token Keycloak itself just issued - no signature check
     *  needed here, since we are the one who received it directly from Keycloak over this
     *  request, not a bearer token presented by an untrusted caller. */
    private Map<String, Object> claimsOf(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return objectMapper.readValue(payload, Map.class);
        } catch (Exception ex) {
            throw new KeycloakProvisioningException("Could not read the issued access token");
        }
    }
}
