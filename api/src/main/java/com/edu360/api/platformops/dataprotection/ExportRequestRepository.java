package com.edu360.api.platformops.dataprotection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportRequestRepository extends JpaRepository<ExportRequest, UUID> {
    List<ExportRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<ExportRequest> findFirstByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<ExportRequest> findAllByOrderByCreatedAtDesc();
    List<ExportRequest> findByStatusOrderByCreatedAtDesc(String status);
    List<ExportRequest> findByStatusAndTenantIdOrderByCreatedAtDesc(String status, UUID tenantId);
}
