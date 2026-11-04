package com.edu360.api.identity;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UserAccountId> {

    /** Used by DevRoleAssignmentSeeder (config/) to find a real account to seed a dev role
     *  assignment for - deliberately requires an explicit status rather than "any account",
     *  since DEACTIVATED/PENDING_ACTIVATION accounts should never be handed a usable role. */
    List<UserAccount> findByIdTenantIdAndStatus(UUID tenantId, UserAccountStatus status);
}
