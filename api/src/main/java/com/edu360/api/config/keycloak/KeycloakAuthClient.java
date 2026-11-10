package com.edu360.api.config.keycloak;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
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
 *
 * Two-step login (I-01, PD-8) stays inside Keycloak: the realm's direct-grant flow
 * ("edu360 direct grant", see KeycloakRealmSecurityService) asks for the authenticator code for
 * every account carrying mfa_required=true and checks it itself. This class only forwards the
 * code in the "totp" form field.
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

    /** sessionId: the Keycloak user-session id ("sid" claim, or the token response's
     *  "session_state" when a realm omits sid) - null when Keycloak reports neither. Used to tie
     *  a signed-in device row to the session it can later be signed out of (SES-01). */
    public record TokenResult(String accessToken, String refreshToken, long expiresInSeconds, String keycloakId,
                               boolean mustChangePassword, String sessionId) {
        public TokenResult(String accessToken, String refreshToken, long expiresInSeconds, String keycloakId,
                           boolean mustChangePassword) {
            this(accessToken, refreshToken, expiresInSeconds, keycloakId, mustChangePassword, null);
        }
    }

    /** Result of {@link #verifyPassword}. */
    public enum PasswordCheck { VERIFIED, INVALID }

    /**
     * Keycloak refused a password grant because a step of the account's sign-in cannot run yet:
     * either a pending required action ("Account is not fully set up") or a REQUIRED flow step
     * the account has no credential for (in the edu360 direct-grant flow: an account that must
     * use two-step login but has no authenticator yet, which Keycloak answers with a 500
     * "unknown_error"). No token is ever issued in either case.
     */
    public static class AccountSetupRequiredException extends RuntimeException {
        public AccountSetupRequiredException() {
            super("Account is not fully set up");
        }
    }

    public TokenResult login(String realm, String identifier, String password) {
        return login(realm, identifier, password, null);
    }

    /**
     * I-01 (PD-8): password grant plus, when the caller has one, the authenticator code in the
     * "totp" form field. A wrong or missing code is Keycloak's ordinary invalid_grant (401 here)
     * and counts towards Keycloak's own brute-force lockout (I-03).
     */
    public TokenResult login(String realm, String identifier, String password, String totp) {
        return requestToken(realm, form -> {
            form.add("grant_type", "password");
            form.add("username", identifier);
            form.add("password", password);
            if (totp != null && !totp.isBlank()) {
                form.add("totp", totp.trim());
            }
        });
    }

    public TokenResult refresh(String realm, String refreshToken) {
        return requestToken(realm, form -> {
            form.add("grant_type", "refresh_token");
            form.add("refresh_token", refreshToken);
        });
    }

    /**
     * I-01/I-03: checks a password WITHOUT creating a session, through the realm's confidential
     * "edu360-password-check" client, whose direct-grant flow is Username -> Password -> Deny
     * access (see KeycloakRealmSecurityService):
     * - wrong password, unknown user or a locked account: Keycloak's JSON invalid_grant -> INVALID;
     * - right password: the Deny-access step answers 401 with its own error page carrying
     *   {@link KeycloakRealmSecurityService#PASSWORD_VERIFIED_MESSAGE} -> VERIFIED.
     * No token and no session is ever issued by that client. Each call is one failed attempt in
     * Keycloak's brute-force record (the deny step is a failure, so this can never be used to
     * reset the counter); the next successful sign-in clears it, as for any failure.
     */
    public PasswordCheck verifyPassword(String realm, String identifier, String password, String clientSecret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", KeycloakRealmSecurityService.PASSWORD_CHECK_CLIENT_ID);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "password");
        form.add("username", identifier);
        form.add("password", password);
        String tokenUrl = properties.getBaseUrl() + "/realms/" + realm + "/protocol/openid-connect/token";
        try {
            restClient.post().uri(tokenUrl).body(form).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            int status = ex.getStatusCode().value();
            String body = ex.getResponseBodyAsString();
            if (status == 401 && body != null && body.contains(KeycloakRealmSecurityService.PASSWORD_VERIFIED_MESSAGE)) {
                return PasswordCheck.VERIFIED;
            }
            if (status == 400 || status == 401) {
                return PasswordCheck.INVALID;
            }
            throw new KeycloakProvisioningException("Keycloak password check failed: HTTP " + status, ex);
        }
        // A 2xx means the realm's password-check flow is not the deny flow. Never treat that as
        // proof of anything - it is a realm configuration fault.
        throw new KeycloakProvisioningException("Realm " + realm + " password-check client issued a token; "
                + "its flow is not configured (run the realm security sync)");
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
            String body = ex.getResponseBodyAsString();
            if (body != null && body.contains("Account is not fully set up")) {
                throw new AccountSetupRequiredException();
            }
            throw new InvalidCredentialsException("Invalid credentials");
        } catch (HttpServerErrorException ex) {
            String body = ex.getResponseBodyAsString();
            if (body != null && body.contains("unknown_error") && "password".equals(form.getFirst("grant_type"))) {
                throw new AccountSetupRequiredException();
            }
            throw new KeycloakProvisioningException("Keycloak token endpoint failed: HTTP " + ex.getStatusCode().value(), ex);
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

        Object sid = claims.get("sid") != null ? claims.get("sid") : response.get("session_state");
        String sessionId = sid == null || sid.toString().isBlank() ? null : sid.toString();

        return new TokenResult(accessToken, refreshTokenValue, expiresIn, subject, mustChangePassword, sessionId);
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
