package com.edu360.api.platformops.usage;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantUsageMonthlyRepository extends JpaRepository<TenantUsageMonthly, UUID> {
    List<TenantUsageMonthly> findByTenantIdOrderByUsageMonthDesc(UUID tenantId);
}
