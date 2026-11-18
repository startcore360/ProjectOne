package com.edu360.api.platformops.dataprotection;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ExportRequestRepository extends JpaRepository<ExportRequest, UUID> {
    List<ExportRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<ExportRequest> findFirstByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<ExportRequest> findAllByOrderByCreatedAtDesc();
    List<ExportRequest> findByStatusOrderByCreatedAtDesc(String status);
    List<ExportRequest> findByStatusAndTenantIdOrderByCreatedAtDesc(String status, UUID tenantId);

    long countByStatus(String status);

    /**
     * E5-S3: a bounded batch in one status, row-locked for the caller's transaction so two application
     * instances cannot claim the same export. Call only inside a read-write transaction on
     * platformTransactionManager (a read-only or foreign transaction cannot hold FOR UPDATE locks).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExportRequest e where e.status = :status order by e.createdAt asc")
    List<ExportRequest> findForUpdateByStatus(@Param("status") String status, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExportRequest e where e.exportRequestId = :id")
    Optional<ExportRequest> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExportRequest e where e.status = 'READY' and e.downloadExpiresAt <= :now"
            + " order by e.downloadExpiresAt asc")
    List<ExportRequest> findReadyToExpireForUpdate(@Param("now") Instant now);

    /** Recently expired exports, re-visited so a file whose delete failed is removed on a later run. */
    List<ExportRequest> findByStatusAndUpdatedAtAfter(String status, Instant after);
}
