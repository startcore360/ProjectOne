package com.edu360.api.approval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DelegationRepository extends JpaRepository<Delegation, DelegationId> {

    List<Delegation> findByIdTenantIdAndDelegatorUserId(UUID tenantId, UUID delegatorUserId);

    List<Delegation> findByIdTenantIdAndDelegateUserId(UUID tenantId, UUID delegateUserId);

    Optional<Delegation> findByIdTenantIdAndIdDelegationId(UUID tenantId, UUID delegationId);
}
