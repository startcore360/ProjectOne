package com.edu360.api.tenant;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, java.util.UUID> {

    /** The one lookup the bootstrap resolver runs: realm name (from the JWT issuer URL, only
     *  used AFTER that issuer has been cryptographically verified) -> which tenant. */
    Optional<Tenant> findByKeycloakRealm(String keycloakRealm);

    /** Used by local-dev hostname-based tenant resolution (abc.edu360.local -> subdomain "abc")
     *  and, in production, by real subdomain-based routing before login even starts. */
    Optional<Tenant> findBySubdomain(String subdomain);

    long countByState(TenantState state);
}
