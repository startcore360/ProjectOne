package com.edu360.api.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UserAccountId> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserAccount u SET u.status = :newStatus WHERE u.id.tenantId = :tenantId AND u.id.userAccountId = :userAccountId AND u.status = :expectedStatus")
    int updateStatusIfMatches(
            @Param("tenantId") UUID tenantId,
            @Param("userAccountId") UUID userAccountId,
            @Param("newStatus") UserAccountStatus newStatus,
            @Param("expectedStatus") UserAccountStatus expectedStatus
    );

    /** Used by DevRoleAssignmentSeeder (config/) to find a real account to seed a dev role
     *  assignment for - deliberately requires an explicit status rather than "any account",
     *  since DEACTIVATED/PENDING_ACTIVATION accounts should never be handed a usable role. */
    List<UserAccount> findByIdTenantIdAndStatus(UUID tenantId, UserAccountStatus status);

    boolean existsByIdTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByIdTenantIdAndMobile(UUID tenantId, String mobile);

    Optional<UserAccount> findByIdTenantIdAndMobileAndUserType(UUID tenantId, String mobile, UserType userType);

    long countByIdTenantIdAndStatusNot(UUID tenantId, UserAccountStatus status);
}
