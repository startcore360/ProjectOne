package com.edu360.api.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityLinkRepository extends JpaRepository<UserIdentityLink, UserIdentityLinkId> {

    /**
     * Finds the active identity link for a (tenant, realm, subject) triple - the one query the
     * JWT→user resolution step runs (see TenantUserLookup, used by both
     * TenantIssuerAuthenticationManagerResolver and AuthService.login).
     *
     * SECURITY: tenantId here must always be the tenant the bootstrap resolver already
     * established from platform.tenant AFTER verifying the JWT's signature and issuer - never a
     * value taken from an unverified claim, a header, or a request parameter. This method does
     * not and cannot enforce that itself; it is the caller's job. Postgres RLS (app.tenant_id)
     * is the second, independent layer underneath this - even a bug in the caller's tenantId
     * handling still cannot leak another tenant's row, because the session-level RLS filter
     * applies regardless of what WHERE clause Hibernate generates here.
     */
    Optional<UserIdentityLink> findByIdTenantIdAndIdentityRealmAndIdentitySubjectAndStatus(
            UUID tenantId, String identityRealm, String identitySubject, IdentityLinkStatus status);

    /** Used by AuthService.changeTenantPassword to find out which field (email/student code/
     *  mobile) this user actually logs in with, so the current-password re-verification call
     *  uses the right identifier - a user can in principle have more than one active link
     *  (multiple login methods); this takes the first one found, a reasonable simplification
     *  flagged rather than silently assumed to handle that case fully. */
    Optional<UserIdentityLink> findFirstByIdTenantIdAndUserAccountIdAndStatus(
            UUID tenantId, UUID userAccountId, IdentityLinkStatus status);
}
