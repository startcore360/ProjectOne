package com.edu360.api.platformops.dataprotection;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RetentionChangeRequestRepository extends JpaRepository<RetentionChangeRequest, UUID> {
    List<RetentionChangeRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
