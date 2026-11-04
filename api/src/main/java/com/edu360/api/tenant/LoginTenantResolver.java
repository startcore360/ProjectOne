package com.edu360.api.tenant;

import org.springframework.stereotype.Component;

import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.InvalidCredentialsException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * WHAT: works out which Keycloak realm a LOGIN attempt should go to, before any token exists at
 * all - a different problem from TenantIssuerAuthenticationManagerResolver, which only ever
 * runs on an ALREADY-ISSUED bearer token's "iss" claim. At login time there is no token yet, so
 * the only thing to go on is which hostname the request came in on.
 *
 * HOW: the Host header's hostname, with the configured local-dev suffix stripped (blank in
 * production). "platform.edu360.local" -> platform login. Anything else -> look that remaining
 * label up against platform.tenant.subdomain (never RLS'd - this is the one query allowed to
 * run before any tenant context exists, same as the bootstrap resolver). Unknown hostname, or a
 * tenant not in ACTIVE/TRIAL state -> rejected outright, never a default tenant.
 *
 * WHY this cannot simply reuse TenantIssuerAuthenticationManagerResolver: that class answers
 * "which validator checks this already-signed token", keyed by a cryptographically-bound issuer
 * claim. This class answers "which realm should Keycloak even attempt a password check
 * against", keyed by nothing stronger than the Host header a browser sent - there is no token
 * to verify yet, so there is nothing to cryptographically trust here at all. The security
 * boundary for login itself is Keycloak's own password check, not this resolution step.
 */
@Component
@RequiredArgsConstructor
public class LoginTenantResolver {

    private final KeycloakProperties keycloakProperties;
    private final TenantRepository tenantRepository;

    public sealed interface Resolution permits PlatformLogin, TenantLogin {
    }

    public record PlatformLogin(String realm) implements Resolution {
    }

    public record TenantLogin(Tenant tenant) implements Resolution {
    }

    public Resolution resolve(HttpServletRequest request) {
        String host = request.getServerName();
        String label = stripLocalDevSuffix(host);

        if (label.equals(keycloakProperties.getPlatformHostLabel())) {
            return new PlatformLogin(keycloakProperties.getPlatformRealm());
        }

        Tenant tenant = tenantRepository.findBySubdomain(label)
                .filter(t -> t.getState() == TenantState.ACTIVE || t.getState() == TenantState.TRIAL)
                .orElseThrow(() -> new InvalidCredentialsException("Unknown sign-in address"));
        return new TenantLogin(tenant);
    }

    private String stripLocalDevSuffix(String host) {
        String suffix = keycloakProperties.getLocalDevHostSuffix();
        if (!suffix.isBlank() && host.endsWith(suffix)) {
            return host.substring(0, host.length() - suffix.length());
        }
        return host;
    }
}
