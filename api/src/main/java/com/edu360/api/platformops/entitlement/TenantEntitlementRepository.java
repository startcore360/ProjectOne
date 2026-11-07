package com.edu360.api.platformops.entitlement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantEntitlementRepository extends JpaRepository<TenantEntitlement, UUID> {
    List<TenantEntitlement> findByTenantId(UUID tenantId);
    Optional<TenantEntitlement> findByTenantIdAndModuleCodeAndFeatureCode(UUID tenantId, String moduleCode, String featureCode);
}
