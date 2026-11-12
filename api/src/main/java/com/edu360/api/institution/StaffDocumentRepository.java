package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffDocumentRepository extends JpaRepository<StaffDocument, StaffDocumentId> {
    List<StaffDocument> findByIdTenantIdAndStaffProfileIdOrderByCreatedAtDesc(UUID tenantId, UUID staffProfileId);
    Optional<StaffDocument> findByIdTenantIdAndIdStaffDocumentId(UUID tenantId, UUID staffDocumentId);
    boolean existsByIdTenantIdAndFileId(UUID tenantId, UUID fileId);
}
