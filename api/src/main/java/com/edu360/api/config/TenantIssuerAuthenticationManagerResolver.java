package com.edu360.api.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.stereotype.Component;

import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.InvalidCredentialsException;
import com.edu360.api.identity.TenantUserLookup;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platform.PlatformUserStatus;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

/**
 * WHAT: picks which validator checks an incoming bearer token, based on which Keycloak realm
 * issued it, and only trusts a tenant/user identity once that validator has independently
 * confirmed the token's signature and issuer are real.
 *
 * HOW the whole flow works, step by step:
 *   1. Spring Security peeks at the token's "iss" claim WITHOUT verifying anything yet, and
 *      calls resolve(issuer) below with that raw string. At this point "issuer" could be
 *      anything a caller typed into a forged token - it is not trusted for ANYTHING except
 *      picking which validator to build next.
 *   2. This class checks the raw string has the exact shape "{our Keycloak base URL}/realms/
 *      {single path segment}" - if not, or if it tries to look like a different host or sneaks
 *      in a "/", "?" or "#", it is rejected immediately with no network call at all. This stops
 *      a forged "iss" claim from making this server fetch metadata from an attacker's URL
 *      (an SSRF angle on JwtDecoders.fromIssuerLocation, which this class never hands a raw
 *      caller-supplied string to - see trustedIssuerUrl()).
 *   3. The extracted realm name is looked up against platform.tenant (tenant realms) or the
 *      one configured platform realm. Unknown realm, or a tenant whose state is not ACTIVE/
 *      TRIAL (SUSPENDED/ARCHIVED/DELETED/CREATED) -> rejected. No fallback to any default
 *      tenant or database ever happens here.
 *   4. Only once a realm is matched does this class build a decoder FROM A URL IT RECONSTRUCTED
 *      ITSELF (never the raw claim), fetch that realm's real public keys, and hand the token to
 *      Spring's own JwtAuthenticationProvider, which does the actual cryptographic signature +
 *      issuer + expiry verification. If that fails, authentication fails here and none of the
 *      code below ever runs.
 *   5. Only on successful verification does the JwtAuthenticationConverter below run - and only
 *      there does this class call TenantContext.set(...) (for a tenant realm) or look up a
 *      PlatformUser (for the platform realm, which deliberately never calls TenantContext.set
 *      at all - see PlatformPrincipal's class comment).
 *
 * This whole class answers one of this piece's three non-negotiable rules directly: an
 * unverified claim is used only to pick a validator, never to establish identity by itself.
 */
@Component
public class TenantIssuerAuthenticationManagerResolver implements AuthenticationManagerResolver<String> {

    private final KeycloakProperties keycloakProperties;
    private final TenantRepository tenantRepository;
    private final PlatformUserRepository platformUserRepository;
    private final TenantUserLookup tenantUserLookup;

    // Each realm's AuthenticationManager (and the JwtDecoder/JWKS fetch inside it) is built
    // once and reused - JwtDecoders.fromIssuerLocation makes a real network call, so this cache
    // is what stops every single request from re-fetching the same realm's keys.
    private final Map<String, AuthenticationManager> managersByIssuer = new ConcurrentHashMap<>();

    public TenantIssuerAuthenticationManagerResolver(KeycloakProperties keycloakProperties,
                                                       TenantRepository tenantRepository,
                                                       PlatformUserRepository platformUserRepository,
                                                       TenantUserLookup tenantUserLookup) {
        this.keycloakProperties = keycloakProperties;
        this.tenantRepository = tenantRepository;
        this.platformUserRepository = platformUserRepository;
        this.tenantUserLookup = tenantUserLookup;
    }

    @Override
    public AuthenticationManager resolve(String issuer) {
        return managersByIssuer.computeIfAbsent(issuer, this::buildManagerOrRejectClosed);
    }

    public void evictTenantIssuer(String realm) {
        if (realm != null) {
            managersByIssuer.remove(trustedIssuerUrl(realm));
        }
    }

    public void evictAll() {
        managersByIssuer.clear();
    }

    private AuthenticationManager buildManagerOrRejectClosed(String issuer) {
        String realm = extractKnownRealmOrNull(issuer);
        if (realm == null) {
            // Fail closed: an issuer we do not recognise is never given the benefit of the
            // doubt, and we never fall back to treating it as some default realm/tenant.
            throw new InvalidBearerTokenException("Unknown token issuer");
        }

        if (realm.equals(keycloakProperties.getPlatformRealm())) {
            return buildPlatformAuthenticationManager(realm);
        }

        Tenant tenant = tenantRepository.findByKeycloakRealm(realm)
                .filter(t -> t.getState() == TenantState.ACTIVE || t.getState() == TenantState.TRIAL)
                .orElseThrow(() -> new InvalidBearerTokenException("Unknown token issuer"));
        return buildTenantAuthenticationManager(realm, tenant);
    }

    /** Returns the realm name if (and only if) "issuer" has exactly the shape our own Keycloak
     *  produces, with no extra path segments or special characters smuggled in - otherwise
     *  null. This runs BEFORE any lookup or network call, so a malformed/foreign issuer never
     *  gets that far. */
    private String extractKnownRealmOrNull(String issuer) {
        String expectedPrefix = keycloakProperties.getBaseUrl() + "/realms/";
        if (issuer == null || !issuer.startsWith(expectedPrefix)) {
            return null;
        }
        String realm = issuer.substring(expectedPrefix.length());
        boolean looksLikeASingleRealmName = !realm.isEmpty()
                && !realm.contains("/") && !realm.contains("?") && !realm.contains("#");
        return looksLikeASingleRealmName ? realm : null;
    }

    /** Builds the issuer URL this server trusts, FROM the already-validated realm name - never
     *  from the raw caller-supplied string, even though by this point the two are guaranteed to
     *  be identical. Passing the reconstructed value (not the original) is what makes it
     *  impossible for a crafted "iss" claim to reach JwtDecoders.fromIssuerLocation at all. */
    private String trustedIssuerUrl(String realm) {
        return keycloakProperties.getBaseUrl() + "/realms/" + realm;
    }

    private AuthenticationManager buildTenantAuthenticationManager(String realm, Tenant tenant) {
        JwtDecoder decoder = JwtDecoders.fromIssuerLocation(trustedIssuerUrl(realm));
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(jwt -> convertVerifiedTenantJwt(tenant, realm, jwt));
        return provider::authenticate;
    }

    /**
     * Runs only after JwtAuthenticationProvider has already called decoder.decode(jwt) and that
     * succeeded - meaning signature, issuer and expiry are all cryptographically confirmed by
     * this point. This is deliberately the ONLY place tenant.getTenantId() is treated as real
     * and handed to TenantContext.set(...).
     */
    private AbstractAuthenticationToken convertVerifiedTenantJwt(Tenant tenant, String realm, Jwt jwt) {
        // BS-26: Verify token azp / aud to reject tokens issued to admin-cli or unauthorized clients
        validateClientAudience(jwt);

        // C5: Real-time tenant state check so suspended/archived tenants lose access immediately without process restart
        tenantRepository.findById(tenant.getTenantId())
                .filter(t -> t.getState() == TenantState.ACTIVE || t.getState() == TenantState.TRIAL)
                .orElseThrow(() -> {
                    evictTenantIssuer(realm);
                    return new InvalidBearerTokenException("Tenant is suspended, archived, or inactive");
                });

        TenantUserLookup.ResolvedUser resolved;
        try {
            resolved = tenantUserLookup.resolveActiveUser(tenant, realm, jwt.getSubject());
        } catch (InvalidCredentialsException ex) {
            // Translated here, not thrown directly by TenantUserLookup: this converter runs
            // inside Spring Security's OAuth2 resource server pipeline, which needs an
            // AuthenticationException (InvalidBearerTokenException) to produce the correct 401
            // + WWW-Authenticate response - a plain InvalidCredentialsException (what
            // AuthService.login uses instead, a world with no such pipeline) would just bubble
            // up as an unhandled 500.
            throw new InvalidBearerTokenException(ex.getMessage());
        }

        boolean mustChangePassword = Boolean.TRUE.equals(jwt.getClaimAsBoolean("must_change_password"));
        TenantPrincipal principal = new TenantPrincipal(
                tenant.getTenantId(), resolved.account().getId().getUserAccountId(),
                resolved.account().getUserType(), resolved.account().getDisplayName(), mustChangePassword);
        // No GrantedAuthority is attached here on purpose: this converter proves WHO the caller
        // is, never WHAT they may do - that is the 71-role/280-action model (Foundation Item 7,
        // not part of this identity migration). Every endpoint stays effectively closed to a
        // tenant principal until that model exists and is wired in.
        return new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
    }

    private AuthenticationManager buildPlatformAuthenticationManager(String realm) {
        JwtDecoder decoder = JwtDecoders.fromIssuerLocation(trustedIssuerUrl(realm));
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(this::convertVerifiedPlatformJwt);
        return provider::authenticate;
    }

    private AbstractAuthenticationToken convertVerifiedPlatformJwt(Jwt jwt) {
        validateClientAudience(jwt);

        // Deliberately no TenantContext.set(...) call anywhere in this method - a platform
        // login must never gain tenant RLS access just by existing (see TenantContext's class
        // comment: with nothing set, every app.* row is correctly invisible).
        PlatformUser user = platformUserRepository.findByIdentitySubject(jwt.getSubject())
                .filter(u -> u.getStatus() == PlatformUserStatus.ACTIVE)
                .orElseThrow(() -> new InvalidBearerTokenException("No active platform account for this identity"));

        boolean mustChangePassword = Boolean.TRUE.equals(jwt.getClaimAsBoolean("must_change_password"));
        PlatformPrincipal principal = new PlatformPrincipal(
                user.getPlatformUserId(), user.getPlatformRole(), user.getEmail(), mustChangePassword);
        return new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
    }

    private void validateClientAudience(Jwt jwt) {
        String azp = jwt.getClaimAsString("azp");
        if ("admin-cli".equalsIgnoreCase(azp) || "account".equalsIgnoreCase(azp)) {
            throw new InvalidBearerTokenException("Tokens issued to internal/admin clients are not allowed for user API access");
        }
        String expectedClient = keycloakProperties.getFrontendClientId();
        if (expectedClient != null && !expectedClient.isBlank()) {
            Object aud = jwt.getClaim("aud");
            boolean matchesAzp = expectedClient.equals(azp);
            boolean matchesAud = (aud instanceof List<?> l && l.contains(expectedClient))
                    || (aud instanceof String s && expectedClient.equals(s));
            if (!matchesAzp && !matchesAud && azp != null) {
                throw new InvalidBearerTokenException("Token audience/azp does not match configured frontend client");
            }
        }
    }
}
