package com.edu360.api.platformops.dataprotection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeletionRequestRepository extends JpaRepository<DeletionRequest, UUID> {
    List<DeletionRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<DeletionRequest> findFirstByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    boolean existsByTenantIdAndStatusIn(UUID tenantId, java.util.Collection<String> statuses);
}
