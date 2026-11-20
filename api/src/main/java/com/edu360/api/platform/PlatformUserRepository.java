package com.edu360.api.platform;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {

    /** No tenant filter here on purpose - this table has no tenant_id and no RLS; it is simply
     *  not part of the tenant-scoped data model at all. */
    Optional<PlatformUser> findByIdentitySubject(String identitySubject);

    @Modifying
    @Transactional
    @Query("update PlatformUser u set u.lastLoginAt = :lastLoginAt where u.platformUserId = :platformUserId")
    int updateLastLogin(@Param("platformUserId") UUID platformUserId, @Param("lastLoginAt") Instant lastLoginAt);

    /** Used by DevAdminSeeder to check idempotently whether the dev admin account already
     *  exists, before Keycloak's own "sub" is known (identitySubject can't be checked by email -
     *  it is Keycloak's own generated subject id, not a login name). */
    Optional<PlatformUser> findByEmail(String email);

    long countByPlatformRoleIdAndStatus(UUID platformRoleId, PlatformUserStatus status);

    long countByPlatformRoleAndStatus(PlatformRole platformRole, PlatformUserStatus status);

    /** Locks all active Product Owners in deterministic order to serialize last-owner changes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from PlatformUser u where u.platformRole = :role and u.status = :status order by u.platformUserId")
    List<PlatformUser> lockActiveProductOwners(@Param("role") PlatformRole role,
            @Param("status") PlatformUserStatus status);

    List<PlatformUser> findAllByPlatformRoleAndStatus(PlatformRole platformRole, PlatformUserStatus status);
}
