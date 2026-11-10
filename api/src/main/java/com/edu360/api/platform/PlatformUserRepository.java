package com.edu360.api.platform;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {

    /** No tenant filter here on purpose - this table has no tenant_id and no RLS; it is simply
     *  not part of the tenant-scoped data model at all. */
    Optional<PlatformUser> findByIdentitySubject(String identitySubject);

    /** Used by DevAdminSeeder to check idempotently whether the dev admin account already
     *  exists, before Keycloak's own "sub" is known (identitySubject can't be checked by email -
     *  it is Keycloak's own generated subject id, not a login name). */
    Optional<PlatformUser> findByEmail(String email);

    long countByPlatformRoleIdAndStatus(UUID platformRoleId, PlatformUserStatus status);

    long countByPlatformRoleAndStatus(PlatformRole platformRole, PlatformUserStatus status);
}
