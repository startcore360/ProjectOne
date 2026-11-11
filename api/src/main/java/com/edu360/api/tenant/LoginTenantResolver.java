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
 * HOW: the Host header's hostname, with a configured local-dev ending stripped (blank in
 * production). Endings may be a dot (abc.edu360.local) or a hyphen (abc-edu360.duckdns.org).
 * "platform.edu360.local" / "platform-edu360.duckdns.org" -> platform login. Anything else -> look that remaining
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

    // PRODUCTION (docker/PRODUCTION_TENANT_ROUTING.txt, P4 and P9): the suffix is blank in production today, so
    // abc.edu360.com is looked up as the full name and login fails. Custom domains aren't read
    // here either. Required before the first production deployment.
    //
    // Several endings may be configured (comma-separated), so more than one address style works
    // at once - e.g. ".edu360.local" (abc.edu360.local) and "-edu360.duckdns.org"
    // (abc-edu360.duckdns.org, one DuckDNS name per tenant). The longest matching ending wins,
    // and what is left must be a single, non-empty label: "x.abc" or "" is never a tenant.
    private String stripLocalDevSuffix(String host) {
        String lowerHost = host == null ? "" : host.toLowerCase(java.util.Locale.ROOT);
        return keycloakProperties.getLocalDevHostSuffixes().stream()
                .sorted(java.util.Comparator.comparingInt(String::length).reversed())
                .filter(lowerHost::endsWith)
                .map(suffix -> lowerHost.substring(0, lowerHost.length() - suffix.length()))
                .filter(label -> !label.isEmpty() && !label.contains("."))
                .findFirst()
                .orElse(lowerHost);
    }
}
