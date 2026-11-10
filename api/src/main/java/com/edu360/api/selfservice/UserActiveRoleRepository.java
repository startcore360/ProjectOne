package com.edu360.api.selfservice;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.edu360.api.identity.UserAccountId;

@Repository
public interface UserActiveRoleRepository extends JpaRepository<UserActiveRole, UserAccountId> {

    Optional<UserActiveRole> findByIdTenantIdAndIdUserAccountId(UUID tenantId, UUID userAccountId);
}
