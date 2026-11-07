package com.edu360.api.platformops.dataprotection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRetentionPolicyRepository extends JpaRepository<TenantRetentionPolicy, UUID> {
    List<TenantRetentionPolicy> findByTenantIdAndStatus(UUID tenantId, String status);
    Optional<TenantRetentionPolicy> findByTenantIdAndDataClassCodeAndStatus(UUID tenantId, String dataClassCode, String status);
}
