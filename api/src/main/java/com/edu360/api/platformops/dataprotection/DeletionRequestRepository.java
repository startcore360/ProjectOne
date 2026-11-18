package com.edu360.api.platformops.dataprotection;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface DeletionRequestRepository extends JpaRepository<DeletionRequest, UUID> {
    List<DeletionRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<DeletionRequest> findFirstByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    boolean existsByTenantIdAndStatusIn(UUID tenantId, java.util.Collection<String> statuses);
    long countByStatus(String status);

    /**
     * Rows the readiness job may move forward, locked for the transaction so two app instances running the
     * daily job at the same time cannot advance (and audit) the same request twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DeletionRequest r where r.status in :statuses order by r.createdAt asc")
    List<DeletionRequest> findReadinessCandidatesForUpdate(@Param("statuses") Collection<String> statuses);
}
